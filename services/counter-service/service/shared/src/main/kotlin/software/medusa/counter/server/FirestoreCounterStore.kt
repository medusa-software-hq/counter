package software.medusa.counter.server

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import software.medusa.counter.server.utils.await
import software.medusa.counter.server.utils.runSuspendTransaction

private const val counterCollectionName = "counter"
private const val countFieldName = "count"

class FirestoreCounterStore(
    private val firestore: Firestore,
) : CounterStore {
  companion object {
    fun build(): CounterStore =
        FirestoreCounterStore(firestore = FirestoreOptions.getDefaultInstance().service)
  }

  override suspend fun getCount(counterId: CounterId): Int =
      firestore
          .collection(counterCollectionName)
          .document(counterId.id)
          .get()
          .await()
          .getLong(countFieldName)
          ?.toInt() ?: 0

  override suspend fun incrementAndGetCount(counterId: CounterId): Int = adjust(counterId, +1)

  override suspend fun decrementAndGetCount(counterId: CounterId): Int = adjust(counterId, -1)

  private suspend fun adjust(counterId: CounterId, delta: Int): Int {
    val ref = firestore.collection(counterCollectionName).document(counterId.id)
    return firestore.runSuspendTransaction { transaction ->
      val snapshot = transaction.get(ref).await()
      val next = (snapshot.getLong(countFieldName)?.toInt() ?: 0) + delta
      transaction.set(ref, mapOf(countFieldName to next), SetOptions.merge())
      next
    }
  }
}
