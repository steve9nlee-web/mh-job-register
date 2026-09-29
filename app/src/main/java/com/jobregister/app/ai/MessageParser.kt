package com.jobregister.app.ai

import com.jobregister.app.model.Customer
import java.util.UUID

/**
 * WhatsApp message -> draft jobs, on the phone.
 *
 * A pasted chat is cut into one draft per unit mentioned, and each draft is
 * matched against the same lists the "Create job" form uses: the unit must
 * be a registered customer unit, the service one from the Services tab.
 * Drafts are only suggestions — the person pasting confirms or corrects the
 * unit, service and room before anything is raised, and the job then goes
 * through the normal approval workflow like any other.
 */
object MessageParser {

    /** One job the pasted text seems to ask for, not yet saved. */
    data class Draft(
        val key: String,                 // stable id for the preview list
        val unit: String,                // a registered unit, or "" if none matched
        val unitHint: String,            // the unit as written, when it is not registered
        val service: String,             // a name from the Services list, or ""
        val rooms: String,               // "Room 1" .. "Room All", or ""
        val notes: String,               // the message text, tidied
        val rawMessage: String,          // exactly what was pasted for this job
        val photos: List<ByteArray> = emptyList()
    ) {
        val ready: Boolean get() = unit.isNotBlank() && service.isNotBlank()
    }

    // Apartment codes and names from SPEC §5. The Apartments tab adds to
    // these, so a new building is recognised without an app update.
    private val knownCodes = listOf("L", "OV", "PV", "R", "SA", "TA", "TB", "WA", "WB")
    private val knownNames = mapOf(
        "Luminari" to "L", "Ocean View" to "OV", "Park View" to "PV",
        "Rubica" to "R", "Sea View" to "SA"
    )

    // "[29/09/2026, 10:15:22] Ali: ..." — WhatsApp's copy of several messages.
    private val bracketHeader =
        Regex("""^\s*\[([^\]]*\d{1,2}[:.]\d{2}[^\]]*)\]\s*(?:([^:]{1,40}):\s*)?""")
    // "29/09/2026, 10:15 - Ali: ..." — WhatsApp's chat export.
    private val dashHeader = Regex(
        """^\s*\d{1,4}[/.\-]\d{1,2}[/.\-]\d{1,4},?\s+\d{1,2}[:.]\d{2}(?:[:.]\d{2})?\s*""" +
            """(?:[aApP]\.?\s?[mM]\.?)?\s*-\s*(?:([^:]{1,40}):\s*)?"""
    )

    private val noise = listOf(
        "<media omitted>", "image omitted", "video omitted", "sticker omitted",
        "this message was deleted", "you deleted this message", "<this message was edited>"
    )

    /** One WhatsApp message (or one line, when the text has no headers). */
    private data class Segment(val text: String, val raw: String)

    fun drafts(
        raw: String,
        customers: List<Customer>,
        apartments: Map<String, String>,
        services: List<String>
    ): List<Draft> {
        val units = UnitMatcher(customers, apartments)

        // 1. Cut the paste into messages. Continuation lines of a multi-line
        //    WhatsApp message stay with the message they belong to.
        val lines = raw.replace("‎", "").replace("‏", "").lines()
        val hasHeaders = lines.any { headerOf(it) != null }
        val segments = mutableListOf<Segment>()
        var skipping = false
        for (line in lines) {
            val header = headerOf(line)
            if (hasHeaders && header == null) {
                // A continuation line of the previous message.
                if (skipping || line.isBlank()) continue
                if (segments.isNotEmpty()) {
                    val last = segments.removeAt(segments.lastIndex)
                    segments += Segment(last.text + "\n" + line.trim(), last.raw + "\n" + line)
                    continue
                }
            }
            // A header with no "Name:" is a system line ("Ali joined") — skip it.
            skipping = header != null && header.groupValues.last().isBlank()
            if (skipping) continue
            val body = (if (header != null) line.substring(header.range.last + 1) else line).trim()
            if (body.isBlank()) continue
            segments += Segment(body, line.trim())
        }

        // 2. A message that names a unit starts a new job; a message without
        //    one ("also the kitchen tap") adds to the job before it.
        val blocks = mutableListOf<Segment>()
        for (seg in segments) {
            val text = seg.text.lines()
                .filterNot { l -> noise.any { l.trim().lowercase() == it } }
                .joinToString("\n").trim()
            if (text.isBlank()) continue
            if (units.find(text).isEmpty() && blocks.isNotEmpty()) {
                val last = blocks.removeAt(blocks.lastIndex)
                blocks += Segment(last.text + "\n" + text, last.raw + "\n" + seg.raw)
            } else {
                blocks += Segment(text, seg.raw)
            }
        }

        // 3. One draft per unit named in the block.
        return blocks.flatMap { block ->
            val found = units.find(block.text).ifEmpty { listOf(UnitMatcher.Found("", "")) }
            val notes = block.text.lines().map { it.trim() }.filter { it.isNotBlank() }
                .joinToString(" / ").replace(Regex("""\s+"""), " ").take(300)
            found.map { hit ->
                val registered = customers.firstOrNull { it.unit == hit.registered }
                Draft(
                    key = UUID.randomUUID().toString(),
                    unit = hit.registered,
                    unitHint = if (hit.registered.isBlank()) hit.written else "",
                    service = guessService(block.text, services, registered?.service.orEmpty()),
                    rooms = guessRooms(block.text),
                    notes = notes,
                    rawMessage = block.raw
                )
            }
        }
    }

    private fun headerOf(line: String): MatchResult? =
        bracketHeader.find(line) ?: dashHeader.find(line)

    // ---- units ----

    /** Finds unit numbers in text and resolves them to registered units. */
    private class UnitMatcher(customers: List<Customer>, apartments: Map<String, String>) {

        /** [registered] is the Customers-tab spelling, "" when not registered. */
        data class Found(val registered: String, val written: String)

        private val codes = (MessageParser.knownCodes + apartments.keys.map { it.trim().uppercase() })
            .filter { it.isNotBlank() && it.all(Char::isLetter) }
            .distinct().sortedByDescending { it.length }

        private val names: Map<String, String> = buildMap {
            putAll(MessageParser.knownNames)
            apartments.forEach { (code, name) ->
                if (name.isNotBlank() && code.isNotBlank()) put(name.trim(), code.trim())
            }
        }

        // "L-19-11", "l 19-11", "R13-08", "OV/26/10"
        private val coded = Regex(
            """(?<![A-Za-z0-9])(${codes.joinToString("|") { Regex.escape(it) }})""" +
                """\s*[-/ ]?\s*(\d{1,3})\s*[-/.]\s*(\d{1,3})(?!\d)""",
            RegexOption.IGNORE_CASE
        )

        // "Ocean View 26-10", "Luminari #19-11"
        private val named = Regex(
            "(?<![A-Za-z])(" +
                names.keys.sortedByDescending { it.length }.joinToString("|") { name ->
                    name.split(Regex("""\s+""")).joinToString("""\s*""") { Regex.escape(it) }
                } +
                """)[\s:#,\-]*(\d{1,3})\s*[-/.]\s*(\d{1,3})(?!\d)""",
            RegexOption.IGNORE_CASE
        )

        // "unit 19-11" with no building: accepted only when exactly one
        // registered unit has that floor and number.
        private val bare = Regex("""(?<![A-Za-z0-9\-/.])(\d{1,3})-(\d{1,3})(?![\d\-/])""")

        private val byKey: Map<String, String> = customers.mapNotNull { c ->
            Regex("""^\s*([A-Za-z]+)\s*-?\s*(\d{1,3})\s*-\s*(\d{1,3})\s*$""").find(c.unit)
                ?.let { m -> key(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to c.unit }
        }.toMap()

        private fun key(code: String, floor: String, unit: String) =
            "${code.uppercase()}-${floor.toInt()}-${unit.toInt()}"

        private fun codeForName(name: String): String {
            val squashed = name.replace(Regex("""\s+"""), "").lowercase()
            return names.entries.firstOrNull {
                it.key.replace(Regex("""\s+"""), "").lowercase() == squashed
            }?.value ?: ""
        }

        fun find(text: String): List<Found> {
            val hits = mutableListOf<Pair<Int, Found>>()
            val taken = mutableListOf<IntRange>()
            fun free(range: IntRange) = taken.none { it.first <= range.last && range.first <= it.last }
            fun add(range: IntRange, code: String, floor: String, unit: String) {
                if (!free(range)) return
                taken += range
                val written = "${code.uppercase()}-$floor-$unit"
                hits += range.first to Found(byKey[key(code, floor, unit)] ?: "", written)
            }
            named.findAll(text).forEach { m ->
                add(m.range, codeForName(m.groupValues[1]), m.groupValues[2], m.groupValues[3])
            }
            coded.findAll(text).forEach { m ->
                add(m.range, m.groupValues[1], m.groupValues[2], m.groupValues[3])
            }
            bare.findAll(text).forEach { m ->
                val suffix = "-${m.groupValues[1].toInt()}-${m.groupValues[2].toInt()}"
                val matches = byKey.filterKeys { it.endsWith(suffix) }.values.distinct()
                if (matches.size == 1 && free(m.range)) {
                    taken += m.range
                    hits += m.range.first to Found(matches.single(), matches.single())
                }
            }
            return hits.sortedBy { it.first }.map { it.second }
                .distinctBy { it.registered.ifBlank { it.written } }
        }
    }

    // ---- service and room ----

    private fun has(text: String, vararg patterns: String): Boolean =
        patterns.any { Regex("(?<![a-z])(?:$it)").containsMatchIn(text) }

    private val aircon = arrayOf("air\\s?-?cond?", "a/c(?![a-z])", "ac(?![a-z])")

    /**
     * The service the message most likely asks for, as named in [services].
     * Falls back to the unit's registered service, just as picking the unit
     * on the form does.
     */
    private fun guessService(raw: String, services: List<String>, unitService: String): String {
        val text = raw.lowercase()
        fun named(target: String, orContaining: String): String? =
            services.firstOrNull { it.equals(target, ignoreCase = true) }
                ?: services.firstOrNull { it.contains(orContaining, ignoreCase = true) }
        val registeredCleaning = unitService.takeIf {
            it.contains("clean", ignoreCase = true) && it in services
        }

        val guess = when {
            has(text, "set\\s?c(?![a-z])", "vacant", "move[\\s-]?(out|in)", "deep\\s?clean") ->
                named("Cleaning Set C", "clean")
            has(text, "set\\s?b(?![a-z])") -> named("Cleaning Set B", "clean")
            has(text, "set\\s?a(?![a-z])") -> named("Cleaning Set A", "clean")
            has(text, "chemical", "overhaul") -> named("AirCond Chemical Overhaul", "air")
            has(text, *aircon) && has(
                text, "not cold", "tak sejuk", "leak", "drip", "water", "repair", "rosak",
                "broken", "inspect", "check", "nois", "bunyi", "smell", "error", "not working"
            ) -> named("AirCond Inspection & Repairs", "air")
            has(text, *aircon) -> named("AirCond Normal Service", "air")
            has(text, "pest", "lipas", "cockroach", "roach", "semut", "ants?(?![a-z])",
                "tikus", "rats?(?![a-z])", "termite", "anai", "bed\\s?bug") ->
                named("Pest Control", "pest")
            has(text, "plumb", "pipe", "paip", "leak", "taps?(?![a-z])", "toilet", "clog",
                "choke", "sink", "drain", "flush", "water heater", "shower") ->
                named("Plumbing", "plumb")
            has(text, "clean", "clening", "cuci", "mop", "sweep", "vacuum", "housekeep",
                "bersih") ->
                registeredCleaning ?: named("Cleaning Set A", "clean")
            has(text, "repair", "fix", "broken", "rosak", "door", "lock", "hinge", "window",
                "cabinet", "light", "lamp", "socket", "wiring", "electric", "bulb") ->
                named("General", "general")
            else -> null
        }
        return guess ?: unitService.takeIf { it in services }.orEmpty()
    }

    /** Room choice for the cleaning sets, read from "2 rooms", "room 3", "whole house". */
    private fun guessRooms(raw: String): String {
        val text = raw.lowercase()
        if (has(text, "whole\\s?(house|unit)", "full\\s?house", "entire", "all\\s?rooms?",
                "semua bilik", "satu rumah")) return "Room All"
        val n = Regex("""(?<![a-z0-9\-])(\d)\s*(?:x\s*)?(?:rooms?|bilik|br(?![a-z]))""")
            .find(text)?.groupValues?.get(1)
            ?: Regex("""(?<![a-z])(?:room|bilik)\s*(\d)(?!\d)""").find(text)?.groupValues?.get(1)
        return when (n?.toIntOrNull()) {
            null, 0 -> ""
            1, 2, 3 -> "Room $n"
            else -> "Room All"
        }
    }
}
