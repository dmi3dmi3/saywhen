package com.dmi3dmi3.saywhen.parser

internal object Arbitration {

    fun merge(extractions: List<Extraction>): Extraction {
        val coverage = extractions.map { claimedTokens(it).size }
        val taken = mutableSetOf<Int>()

        fun <T : Any> pick(field: (Extraction) -> T?, tokensOf: (T) -> List<IntRange>, conf: (T) -> Confidence): T? {
            val ranked = extractions.withIndex()
                .mapNotNull { (idx, e) -> field(e)?.let { it to idx } }
                .sortedWith(
                    compareByDescending<Pair<T, Int>> { conf(it.first).weight }
                        .thenByDescending { coverage[it.second] }
                        .thenBy { it.second },
                )
            for ((candidate, _) in ranked) {
                val claimed = tokensOf(candidate).flatMap { it.toList() }
                if (claimed.none { it in taken }) {
                    taken += claimed
                    return candidate
                }
            }
            return null
        }

        val recurrence = pick({ it.recurrence }, { listOf(it.tokens) + it.extraTokens }, { it.confidence })
        val date = pick({ it.date }, { listOf(it.tokens) }, { it.confidence })
        val time = pick({ it.time }, { listOf(it.tokens) }, { it.confidence })
        val duration = pick({ it.duration }, { listOf(it.tokens) }, { it.confidence })
        val reminder = pick({ it.reminder }, { listOf(it.tokens) }, { it.confidence })
        return Extraction(
            recurrence = recurrence,
            date = date,
            time = time,
            duration = duration.takeIf { time != null && time.duration == null },
            reminder = reminder,
        )
    }

    fun coverage(e: Extraction): Int = claimedTokens(e).size

    private fun claimedTokens(e: Extraction): Set<Int> = buildSet {
        e.recurrence?.let { r ->
            addAll(r.tokens)
            r.extraTokens.forEach(::addAll)
        }
        e.date?.let { addAll(it.tokens) }
        e.time?.let { addAll(it.tokens) }
        e.duration?.let { addAll(it.tokens) }
        e.reminder?.let { addAll(it.tokens) }
    }
}
