package software.medusa.counter.server.utils

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import kotlinx.coroutines.coroutineScope

suspend fun <T> Firestore.runSuspendTransaction(
    updateFunction: suspend (Transaction) -> T,
): T = coroutineScope {
  runAsyncTransaction { transaction -> apiFuture { updateFunction(transaction) } }.await()
}
