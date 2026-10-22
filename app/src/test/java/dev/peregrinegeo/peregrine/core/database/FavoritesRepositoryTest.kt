package dev.peregrinegeo.peregrine.core.database

import dev.peregrinegeo.peregrine.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesRepositoryTest {

    @Test
    fun testAddAndRetrieve() {
        val repo = InMemoryFavoritesRepository()
        val fav = repo.add("Office", LatLng(37.7749, -122.4194))
        assertNotNull(fav.id)
        assertEquals("Office", fav.name)

        val retrieved = repo.getById(fav.id)
        assertEquals(fav, retrieved)
    }

    @Test
    fun testSortAlphabetical() {
        val repo = InMemoryFavoritesRepository()
        repo.add("Zebra crossing", LatLng(10.0, 10.0))
        repo.add("Airport", LatLng(20.0, 20.0))
        repo.add("beach", LatLng(30.0, 30.0))

        val sorted = repo.getAll(FavoriteSortOrder.Alphabetical)
        assertEquals(3, sorted.size)
        assertEquals("Airport", sorted[0].name)
        assertEquals("beach", sorted[1].name)
        assertEquals("Zebra crossing", sorted[2].name)
    }

    @Test
    fun testSortRecent() {
        val repo = InMemoryFavoritesRepository()
        val f1 = repo.add("Old", LatLng(10.0, 10.0))
        Thread.sleep(10)
        val f2 = repo.add("Newer", LatLng(20.0, 20.0))

        var recent = repo.getAll(FavoriteSortOrder.Recent)
        assertEquals("Newer", recent[0].name)

        // Mark old as used now
        Thread.sleep(10)
        repo.markUsed(f1.id)
        recent = repo.getAll(FavoriteSortOrder.Recent)
        assertEquals("Old", recent[0].name)
    }

    @Test
    fun testManualReorder() {
        val repo = InMemoryFavoritesRepository()
        val f1 = repo.add("First", LatLng(1.0, 1.0))
        val f2 = repo.add("Second", LatLng(2.0, 2.0))
        val f3 = repo.add("Third", LatLng(3.0, 3.0))

        // Reverse order manually
        repo.reorder(listOf(f3.id, f1.id, f2.id))
        val manual = repo.getAll(FavoriteSortOrder.Manual)
        assertEquals("Third", manual[0].name)
        assertEquals("First", manual[1].name)
        assertEquals("Second", manual[2].name)
    }

    @Test
    fun testDelete() {
        val repo = InMemoryFavoritesRepository()
        val f1 = repo.add("Temp", LatLng(0.0, 0.0))
        assertEquals(1, repo.getAll().size)

        repo.delete(f1.id)
        assertEquals(0, repo.getAll().size)
        assertNull(repo.getById(f1.id))
    }
}
