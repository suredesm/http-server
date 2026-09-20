package lk.fincore

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
)

class UserRepository {
    private val fakeUsers = listOf(
        User(1, "John", "Smith"),
        User(2, "Emma", "Johnson"),
        User(3, "Michael", "Brown"),
        User(4, "Sophia", "Davis"),
        User(5, "Daniel", "Wilson"),
        User(6, "Olivia", "Taylor"),
        User(7, "James", "Anderson"),
        User(8, "Ava", "Thomas"),
        User(9, "William", "Jackson"),
        User(10, "Isabella", "White"),
        User(11, "Benjamin", "Harris"),
        User(12, "Mia", "Martin"),
        User(13, "Lucas", "Thompson"),
        User(14, "Charlotte", "Garcia"),
        User(15, "Henry", "Martinez"),
        User(16, "Amelia", "Robinson"),
        User(17, "Alexander", "Clark"),
        User(18, "Harper", "Rodriguez"),
        User(19, "Ethan", "Lewis"),
        User(20, "Evelyn", "Lee"),
    )

    private val users = mutableListOf<User>().also { it.addAll(fakeUsers) }
    private var nextId = users.maxOf { it.id } + 1

    fun insertUser(user: User): Result<Int> {
        val exists = users.any { it.id == user.id }

        return if (user.id != 0 && exists) Result.failure(RuntimeException("Failed insertion. User with id=${user.id} already exist"))
        else {
            users.add(user.copy(id = user.id.takeIf { it != 0 } ?: nextId))
            val currentId = if (user.id == 0) nextId else user.id
            nextId = 1 + if (user.id == 0) nextId else user.id
            Result.success(currentId)
        }
    }

    fun updateUser(user: User): Result<Int> {
        val existsIndex = users.indexOfFirst { it.id == user.id }

        return if (existsIndex == -1) Result.failure(RuntimeException("Failed update. User with id=${user.id} does not exist"))
        else {
            users[existsIndex] = user
            Result.success(user.id)
        }
    }

    fun deleteUser(userId: Int): Result<Int> {
        val existsIndex = users.indexOfFirst { it.id == userId }

        return if (existsIndex == -1) Result.failure(RuntimeException("Failed deletion. User with id=$userId does not exist"))
        else {
            Result.success(users.removeAt(existsIndex).id)
        }
    }

    fun getAllUsers() = users.toList()

    fun getUser(userId: Int) = users
        .find { it.id == userId }
        .let {
            if (it == null) Result.failure(RuntimeException("User with id=$userId doesn't exists"))
            else Result.success(it)
        }
}