package dev.peregrinegeo.peregrine.core.database

import dev.peregrinegeo.peregrine.core.geo.LatLng
import java.util.UUID

enum class FavoriteSortOrder {
    Recent,
    Alphabetical,
    Manual
}

data class FavoriteLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val point: LatLng,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val manualOrder: Int = 0
)

interface FavoritesRepository {
    fun getAll(sortOrder: FavoriteSortOrder = FavoriteSortOrder.Recent): List<FavoriteLocation>
    fun getById(id: String): FavoriteLocation?
    fun add(name: String, point: LatLng): FavoriteLocation
    fun update(favorite: FavoriteLocation)
    fun delete(id: String)
    fun markUsed(id: String)
    fun reorder(orderedIds: List<String>)
}

/**
 * Thread-safe in-memory repository implementing Favorites management and sorting.
 */
class InMemoryFavoritesRepository : FavoritesRepository {
    private val favorites = mutableMapOf<String, FavoriteLocation>()

    @Synchronized
    override fun getAll(sortOrder: FavoriteSortOrder): List<FavoriteLocation> {
        val list = favorites.values.toList()
        return when (sortOrder) {
            FavoriteSortOrder.Recent -> list.sortedByDescending { it.lastUsedAt }
            FavoriteSortOrder.Alphabetical -> list.sortedBy { it.name.lowercase() }
            FavoriteSortOrder.Manual -> list.sortedBy { it.manualOrder }
        }
    }

    @Synchronized
    override fun getById(id: String): FavoriteLocation? = favorites[id]

    @Synchronized
    override fun add(name: String, point: LatLng): FavoriteLocation {
        val maxOrder = favorites.values.maxOfOrNull { it.manualOrder } ?: -1
        val fav = FavoriteLocation(
            name = name,
            point = point,
            manualOrder = maxOrder + 1
        )
        favorites[fav.id] = fav
        return fav
    }

    @Synchronized
    override fun update(favorite: FavoriteLocation) {
        favorites[favorite.id] = favorite
    }

    @Synchronized
    override fun delete(id: String) {
        favorites.remove(id)
    }

    @Synchronized
    override fun markUsed(id: String) {
        val fav = favorites[id] ?: return
        favorites[id] = fav.copy(lastUsedAt = System.currentTimeMillis())
    }

    @Synchronized
    override fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            val fav = favorites[id]
            if (fav != null) {
                favorites[id] = fav.copy(manualOrder = index)
            }
        }
    }
}
