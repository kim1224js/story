package es.kim.story

import java.text.NumberFormat
import java.util.Locale

enum class EquipmentSlot(val title: String) {
    Hat("모자"), Top("상의"), Bottom("하의"), Shoes("신발"), LeftHand("왼손"), RightHand("오른손")
}

data class EquippedItem(val itemId: Long, val name: String)

data class CharacterEquipment(
    val characterId: Long,
    val equippedItems: Map<EquipmentSlot, EquippedItem?> = EquipmentSlot.entries.associateWith { null },
)

@JvmInline
value class Won(val amount: Long) {
    init { require(amount >= 0) { "재화는 0원보다 작을 수 없습니다." } }
    fun formatted(): String = "${NumberFormat.getNumberInstance(Locale.KOREA).format(amount)}원"
    operator fun plus(other: Won) = Won(Math.addExact(amount, other.amount))
    fun spend(cost: Won): Won? = if (amount >= cost.amount) Won(amount - cost.amount) else null
}
