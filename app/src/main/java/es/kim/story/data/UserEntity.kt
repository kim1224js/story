package es.kim.story.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val userId: String,
    val money: Long = 1_000_000_000L,
    val gender: String = "남성",
    val chapter: Int = 1,
    val seotdaName1: String = "졸린",
    val seotdaName2: String = "토끼",
    val seotdaName3: String = "콜라",
)
