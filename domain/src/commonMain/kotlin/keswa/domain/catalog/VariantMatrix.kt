package keswa.domain.catalog

import keswa.core.common.AppResult

data class OptionValueInput(val valueAr: String, val valueEn: String? = null)
data class OptionInput(val nameAr: String, val nameEn: String? = null, val values: List<OptionValueInput>)

data class GeneratedVariant(val optionValues: List<OptionValueInput>, val nameSuffix: String)

/**
 * Turns Size × Colour (× a third axis, capped) option definitions into the cross-product of
 * variants — pure combinatorics, no I/O, so it is cheap to test exhaustively. See ADR-009: the
 * schema supports up to 3 option axes; nothing here assumes exactly 2.
 */
object VariantMatrix {
    const val MAX_VARIANTS = 200

    fun generate(options: List<OptionInput>): AppResult<List<GeneratedVariant>> {
        val active = options.filter { it.values.isNotEmpty() }
        if (active.isEmpty()) {
            return AppResult.Ok(listOf(GeneratedVariant(emptyList(), "")))
        }

        var combos: List<List<OptionValueInput>> = listOf(emptyList())
        for (option in active) {
            val next = mutableListOf<List<OptionValueInput>>()
            for (combo in combos) {
                for (value in option.values) {
                    next += combo + value
                    if (next.size > MAX_VARIANTS) {
                        return AppResult.Err(CatalogError.TooManyVariants(next.size))
                    }
                }
            }
            combos = next
        }

        return AppResult.Ok(
            combos.map { combo -> GeneratedVariant(combo, combo.joinToString(" / ") { it.valueAr }) },
        )
    }
}
