package com.dmi3dmi3.saywhen.parser

/**
 * Арбитраж кандидатов нескольких трансляторов — сердце мультиязычности.
 * Поля разыгрываются в порядке повтор → дата → время → длительность;
 * победитель поля — по (скор → покрытие транслятора → порядок в списке),
 * кандидат с токенами, пересекающими уже принятые, отбрасывается — берётся
 * следующий. Полный детерминизм: скоры дискретны ([Confidence]), покрытие —
 * счёт токенов, никаких континуальных процентов.
 */
internal object Arbitration {

    /** [extractions] — выходы трансляторов в порядке приоритета. */
    fun merge(extractions: List<Extraction>): Extraction {
        // покрытие — счёт занятых токенов; доля не нужна: знаменатель у всех общий
        val coverage = extractions.map { claimedTokens(it).size }
        val taken = mutableSetOf<Int>()

        /** Лучший непересекающийся кандидат поля по (скор, покрытие, порядок). */
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

        // порядок розыгрыша полей — тот же, что у правил: Rec → Date → Time → Dur
        val recurrence = pick({ it.recurrence }, { listOf(it.tokens) + it.extraTokens }, { it.confidence })
        val date = pick({ it.date }, { listOf(it.tokens) }, { it.confidence })
        val time = pick({ it.time }, { listOf(it.tokens) }, { it.confidence })
        val duration = pick({ it.duration }, { listOf(it.tokens) }, { it.confidence })
        return Extraction(
            recurrence = recurrence,
            date = date,
            time = time,
            // гард: длительность осмысленна только при простом времени — внутри
            // одного транслятора это держит его порядок правил, между языками — мердж
            duration = duration.takeIf { time != null && time.duration == null },
        )
    }

    /** Занятых токенов у Extraction — для выбора транслятора-лидера (дефолтный заголовок). */
    fun coverage(e: Extraction): Int = claimedTokens(e).size

    /** Токены, занятые Extraction, — покрытие транслятора. */
    private fun claimedTokens(e: Extraction): Set<Int> = buildSet {
        e.recurrence?.let { r ->
            addAll(r.tokens)
            r.extraTokens.forEach(::addAll)
        }
        e.date?.let { addAll(it.tokens) }
        e.time?.let { addAll(it.tokens) }
        e.duration?.let { addAll(it.tokens) }
    }
}
