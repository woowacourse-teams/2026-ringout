package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDestinationRepository(
    private val dataSource: DestinationDataSource,
    private val accountProvider: DestinationAccountProvider? = null,
    private val sessionGate: DestinationSyncSessionGate = DestinationSyncSessionGate(),
) : DestinationRepository {
    override fun observeAll(): Flow<List<SavedDestination>> =
        accountProvider?.accountIds?.flatMapLatest(dataSource::observeAll)
            ?: dataSource.observeAll(ownerAccountId = null)

    override suspend fun save(destination: SavedDestination): SavedDestination = withCurrentAccount { account ->
        dataSource.save(destination, account?.ownerAccountId)
    }

    override suspend fun delete(id: Long): Boolean = withCurrentAccount { account ->
        dataSource.delete(id, account?.ownerAccountId)
    }

    private suspend fun <T> withCurrentAccount(block: suspend (DestinationAccountContext?) -> T): T {
        val account = accountProvider?.currentContext() ?: return block(null)
        return sessionGate.withSession(account.sessionIdentity) {
            val current = accountProvider.currentContext()
            if (current == null || current.ownerAccountId != account.ownerAccountId ||
                current.sessionIdentity !== account.sessionIdentity
            ) {
                throw DestinationAccountChanged()
            }
            block(account)
        }
    }
}
