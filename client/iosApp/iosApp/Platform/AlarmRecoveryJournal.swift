import Darwin
import CryptoKit
import Foundation

enum AlarmRecoveryJournalError: Error, Equatable {
    case staleReservationClaim
    case sourceNotEligible
    case missingParentAttempt
    case missingRetryToken
    case retryTokenMismatch
    case terminalChain
    case pastDueReservation
    case legacyConflict
    case missingTerminalChain
    case unsettledReservation
    case unsettledProjection
    case staleCleanupCandidate
    case unsupportedSchemaVersion
}

struct AlarmRecoveryOwner: Codable, Equatable {
    enum Kind: String, Codable {
        case capturedAccount
        case capturedGuest
        case uncaptured
    }

    let kind: Kind
    let accountId: String?

    static func capturedAccount(_ accountId: String) -> AlarmRecoveryOwner {
        AlarmRecoveryOwner(kind: .capturedAccount, accountId: accountId)
    }

    static let capturedGuest = AlarmRecoveryOwner(kind: .capturedGuest, accountId: nil)
    static let uncaptured = AlarmRecoveryOwner(kind: .uncaptured, accountId: nil)
}

struct AlarmRecoveryDestinationSnapshot: Codable, Equatable {
    let name: String
    let latitude: Double
    let longitude: Double
}

struct AlarmRecoverySourceSnapshot: Codable, Equatable {
    let sourceAlarmId: String
    let scheduleVersion: Int64
    let destination: AlarmRecoveryDestinationSnapshot
    let limitSeconds: Int64
    let scheduledAtEpochMillis: Int64?
    let originalTime: String?
    let arrivalRadiusMeters: Double?
    let repeatRule: String?
    let title: String?
    let soundName: String?
    let soundURI: String?
    let owner: AlarmRecoveryOwner
}

struct AlarmRecoverySourceKey: Codable, Hashable {
    let sourceAlarmId: String
    let scheduleVersion: Int64
}

enum AlarmRecoveryEligibility: String, Codable {
    case enabled
    case disabled
    case deleted
    case unknown
}

enum AlarmRecoveryReservationStatus: String, Codable, Equatable {
    case pending
    case inFlight
    case confirmed
    case failed
    case unknown
    case absent
    case cancellationRequired
    case cancellationConfirmed
}

enum AlarmRecoveryAttemptOutcome: String, Codable, Equatable {
    case active
    case expired
    case terminalSucceeded
    case terminalForcedStop
    case skippedUnsupportedOverlap
}

struct AlarmRecoveryReservationClaim: Codable, Equatable {
    let occurrenceId: String
    let operationId: String
    let generation: Int64
    let reservationUUID: String
    let executionEpoch: String
}

struct AlarmRecoveryReservationLedger: Codable, Equatable {
    let reservationUUID: String
    var status: AlarmRecoveryReservationStatus
    var operationId: String?
    var generation: Int64
    var executionEpoch: String?
    var scheduleCallSettled: Bool
    var lastUpdatedEpochMillis: Int64
    var lastError: String?
    var cancellationRequired: Bool
    var cancellationConfirmedAtEpochMillis: Int64?
    var lateScheduleAfterTerminal: Bool
}

struct AlarmRecoveryAttempt: Codable, Equatable {
    let occurrenceId: String
    let chainId: String
    let parentOccurrenceId: String?
    let sequence: Int
    let sourceKey: AlarmRecoverySourceKey
    let snapshot: AlarmRecoverySourceSnapshot
    let stopDedupKey: String
    let stopAtEpochMillis: Int64
    let scheduledAtEpochMillis: Int64?
    let expiresAtEpochMillis: Int64
    let nextReservationUUID: String
    let nextOccurrenceToken: String
    var currentReservation: AlarmRecoveryReservationLedger
    var missionProjectionAck: Bool
    var recordProjectionAck: Bool
    var outcome: AlarmRecoveryAttemptOutcome
}

struct AlarmRecoveryChain: Codable, Equatable {
    let chainId: String
    var terminalOutcome: AlarmRecoveryAttemptOutcome?
    var terminalAtEpochMillis: Int64?
    var terminalRecordAck: Bool
    var terminalOccurrenceId: String? = nil
    var attempts: [AlarmRecoveryAttempt]
}

struct AlarmRecoveryPresentationObservation: Codable, Equatable {
    let stage: String
    let atEpochMillis: Int64
    let mode: String
    let canContinueInForeground: Bool
    let protectedDataAvailable: Bool
}

struct AlarmRecoveryTerminalTombstone: Codable, Equatable {
    let chainId: String
    let chainFingerprint: String
    let sourceKey: AlarmRecoverySourceKey
    let terminalOccurrenceId: String
    let terminalOutcome: AlarmRecoveryAttemptOutcome
    let terminalAtEpochMillis: Int64
    let knownTokens: [String]
    let retryReservationUUIDByToken: [String: String]
    let tokenlessRootStopAtEpochMillis: [Int64]
    let reservationUUIDs: [String]
    var cancellationRequiredReservationUUIDs: [String]
}

enum AlarmRecoveryJournalStopRoute: Equatable {
    case liveAttempt(AlarmRecoveryAttempt)
    case compactedTerminal(AlarmRecoveryTerminalTombstone)
    case unknownRetry
    case newRoot
}

struct AlarmRecoveryState: Codable, Equatable {
    static let currentSchemaVersion = 2

    var schemaVersion: Int
    var snapshots: [AlarmRecoverySourceKey: AlarmRecoverySourceSnapshot]
    var sources: [AlarmRecoverySourceKey: AlarmRecoveryEligibility]
    var chains: [String: AlarmRecoveryChain]
    var tokenIndex: [String: String]
    var rootWindowIndex: [String: String]
    var terminalTombstones: [String: AlarmRecoveryTerminalTombstone]
    var presentationObservations: [AlarmRecoveryPresentationObservation]? = nil

    static let empty = AlarmRecoveryState(
        schemaVersion: currentSchemaVersion,
        snapshots: [:],
        sources: [:],
        chains: [:],
        tokenIndex: [:],
        rootWindowIndex: [:],
        terminalTombstones: [:]
    )

    private enum CodingKeys: String, CodingKey {
        case schemaVersion
        case snapshots
        case sources
        case chains
        case tokenIndex
        case rootWindowIndex
        case terminalTombstones
        case presentationObservations
    }

    init(
        schemaVersion: Int,
        snapshots: [AlarmRecoverySourceKey: AlarmRecoverySourceSnapshot],
        sources: [AlarmRecoverySourceKey: AlarmRecoveryEligibility],
        chains: [String: AlarmRecoveryChain],
        tokenIndex: [String: String],
        rootWindowIndex: [String: String],
        terminalTombstones: [String: AlarmRecoveryTerminalTombstone],
        presentationObservations: [AlarmRecoveryPresentationObservation]? = nil
    ) {
        self.schemaVersion = schemaVersion
        self.snapshots = snapshots
        self.sources = sources
        self.chains = chains
        self.tokenIndex = tokenIndex
        self.rootWindowIndex = rootWindowIndex
        self.terminalTombstones = terminalTombstones
        self.presentationObservations = presentationObservations
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedVersion = try container.decode(Int.self, forKey: .schemaVersion)
        snapshots = try container.decode([AlarmRecoverySourceKey: AlarmRecoverySourceSnapshot].self, forKey: .snapshots)
        sources = try container.decode([AlarmRecoverySourceKey: AlarmRecoveryEligibility].self, forKey: .sources)
        chains = try container.decode([String: AlarmRecoveryChain].self, forKey: .chains)
        tokenIndex = try container.decode([String: String].self, forKey: .tokenIndex)
        rootWindowIndex = try container.decode([String: String].self, forKey: .rootWindowIndex)
        if decodedVersion == 1 {
            terminalTombstones = [:]
        } else if decodedVersion == Self.currentSchemaVersion {
            terminalTombstones = try container.decode(
                [String: AlarmRecoveryTerminalTombstone].self,
                forKey: .terminalTombstones
            )
        } else {
            terminalTombstones = try container.decodeIfPresent(
                [String: AlarmRecoveryTerminalTombstone].self,
                forKey: .terminalTombstones
            ) ?? [:]
        }
        presentationObservations = try container.decodeIfPresent(
            [AlarmRecoveryPresentationObservation].self,
            forKey: .presentationObservations
        )
        schemaVersion = decodedVersion == 1 ? Self.currentSchemaVersion : decodedVersion
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(schemaVersion, forKey: .schemaVersion)
        try container.encode(snapshots, forKey: .snapshots)
        try container.encode(sources, forKey: .sources)
        try container.encode(chains, forKey: .chains)
        try container.encode(tokenIndex, forKey: .tokenIndex)
        try container.encode(rootWindowIndex, forKey: .rootWindowIndex)
        try container.encode(terminalTombstones, forKey: .terminalTombstones)
        try container.encodeIfPresent(presentationObservations, forKey: .presentationObservations)
    }
}

struct AlarmRecoveryAcceptedStop: Equatable {
    let attempt: AlarmRecoveryAttempt
    let duplicate: Bool
}

final class AlarmRecoveryJournal {
    private let directoryURL: URL?
    private let uuidProvider: () -> String
    private let coalescingWindowMillis: Int64 = 10_000

    init(directoryURL: URL? = nil, uuidProvider: @escaping () -> String = { UUID().uuidString }) {
        self.directoryURL = directoryURL
        self.uuidProvider = uuidProvider
    }

    func setSourceEligibility(
        sourceAlarmId: String,
        scheduleVersion: Int64,
        eligibility: AlarmRecoveryEligibility
    ) throws {
        try mutate { state in
            state.sources[AlarmRecoverySourceKey(sourceAlarmId: sourceAlarmId, scheduleVersion: scheduleVersion)] = eligibility
            return ()
        }
    }

    func upsertSourceSnapshot(_ snapshot: AlarmRecoverySourceSnapshot) throws -> AlarmRecoverySourceSnapshot {
        try mutate { state in
            let key = AlarmRecoverySourceKey(
                sourceAlarmId: snapshot.sourceAlarmId,
                scheduleVersion: snapshot.scheduleVersion
            )
            if let existing = state.snapshots[key] {
                return existing
            }
            state.snapshots[key] = snapshot
            return snapshot
        }
    }

    func sourceSnapshot(sourceAlarmId: String, scheduleVersion: Int64) throws -> AlarmRecoverySourceSnapshot? {
        try lockedRead().snapshots[AlarmRecoverySourceKey(
            sourceAlarmId: sourceAlarmId,
            scheduleVersion: scheduleVersion
        )]
    }

    func acceptStop(
        snapshot: AlarmRecoverySourceSnapshot,
        occurrenceToken: String?,
        retryAttempt: Int? = nil,
        parentOccurrenceId: String? = nil,
        rootOccurrenceId: String? = nil,
        stopAt: Date = Date()
    ) throws -> AlarmRecoveryAcceptedStop {
        try mutate { state in
            let nowMillis = Self.epochMillis(stopAt)
            let sourceKey = AlarmRecoverySourceKey(
                sourceAlarmId: snapshot.sourceAlarmId,
                scheduleVersion: snapshot.scheduleVersion
            )
            let isRetry = parentOccurrenceId != nil

            if state.compactedTerminal(
                occurrenceToken: occurrenceToken,
                sourceKey: sourceKey,
                stopAtEpochMillis: nowMillis,
                windowMillis: coalescingWindowMillis
            ) != nil {
                throw AlarmRecoveryJournalError.terminalChain
            }

            let dedupKey: String
            if let occurrenceToken {
                dedupKey = "token|\(occurrenceToken)"
                if let existingOccurrence = state.tokenIndex[dedupKey],
                   let existing = state.findAttempt(occurrenceId: existingOccurrence) {
                    return AlarmRecoveryAcceptedStop(attempt: existing, duplicate: true)
                }
            } else {
                dedupKey = "root-window|\(snapshot.sourceAlarmId)|\(snapshot.scheduleVersion)|\(nowMillis)"
                if let existing = state.findRootAttempt(
                    sourceKey: sourceKey,
                    stopAtEpochMillis: nowMillis,
                    windowMillis: coalescingWindowMillis
                ) {
                    return AlarmRecoveryAcceptedStop(attempt: existing, duplicate: true)
                }
            }

            let parent = try parentOccurrenceId.map { parentId -> AlarmRecoveryAttempt in
                guard let parentAttempt = state.findAttempt(occurrenceId: parentId) else {
                    throw AlarmRecoveryJournalError.missingParentAttempt
                }
                return parentAttempt
            }
            if let retryAttempt {
                guard retryAttempt == 0 || parent != nil else {
                    throw AlarmRecoveryJournalError.missingParentAttempt
                }
                if let parent, retryAttempt != parent.sequence + 1 {
                    throw AlarmRecoveryJournalError.retryTokenMismatch
                }
            }
            if let parent {
                guard state.chains[parent.chainId]?.terminalOutcome == nil else {
                    throw AlarmRecoveryJournalError.terminalChain
                }
                guard let occurrenceToken, occurrenceToken == parent.nextOccurrenceToken else {
                    throw AlarmRecoveryJournalError.retryTokenMismatch
                }
            } else {
                let eligibility = state.sources[sourceKey]
                guard eligibility == .enabled else {
                    throw AlarmRecoveryJournalError.sourceNotEligible
                }
                if state.snapshots[sourceKey] == nil {
                    state.snapshots[sourceKey] = snapshot
                }
            }
            let chainId = parent?.chainId ?? rootOccurrenceId ?? uuidProvider()
            let acceptedSnapshot = parent?.snapshot ?? snapshot
            let acceptedSourceKey = parent?.sourceKey ?? sourceKey
            let occurrenceId: String
            if isRetry {
                guard let occurrenceToken else { throw AlarmRecoveryJournalError.missingRetryToken }
                occurrenceId = occurrenceToken
            } else {
                occurrenceId = chainId
            }
            let sequence = (parent?.sequence ?? -1) + 1
            let reservationUUID = uuidProvider()
            let nextToken = uuidProvider()
            let expiresAt = nowMillis + acceptedSnapshot.limitSeconds * 1_000
            let ledger = AlarmRecoveryReservationLedger(
                reservationUUID: reservationUUID,
                status: .pending,
                operationId: nil,
                generation: 0,
                executionEpoch: nil,
                scheduleCallSettled: false,
                lastUpdatedEpochMillis: nowMillis,
                lastError: nil,
                cancellationRequired: false,
                cancellationConfirmedAtEpochMillis: nil,
                lateScheduleAfterTerminal: false
            )
            let attempt = AlarmRecoveryAttempt(
                occurrenceId: occurrenceId,
                chainId: chainId,
                parentOccurrenceId: parentOccurrenceId,
                sequence: sequence,
                sourceKey: acceptedSourceKey,
                snapshot: acceptedSnapshot,
                stopDedupKey: dedupKey,
                stopAtEpochMillis: nowMillis,
                scheduledAtEpochMillis: parent?.expiresAtEpochMillis ?? acceptedSnapshot.scheduledAtEpochMillis,
                expiresAtEpochMillis: expiresAt,
                nextReservationUUID: reservationUUID,
                nextOccurrenceToken: nextToken,
                currentReservation: ledger,
                missionProjectionAck: false,
                recordProjectionAck: false,
                outcome: .active
            )
            if state.chains[chainId] == nil {
                state.chains[chainId] = AlarmRecoveryChain(
                    chainId: chainId,
                    terminalOutcome: nil,
                    terminalAtEpochMillis: nil,
                    terminalRecordAck: false,
                    attempts: []
                )
            }
            state.chains[chainId]?.attempts.append(attempt)
            if occurrenceToken != nil {
                state.tokenIndex[dedupKey] = occurrenceId
            } else {
                state.rootWindowIndex[dedupKey] = occurrenceId
            }
            return AlarmRecoveryAcceptedStop(attempt: attempt, duplicate: false)
        }
    }

    func routeStop(
        occurrenceToken: String?,
        retryAttempt: Int,
        sourceKey: AlarmRecoverySourceKey,
        stopAt: Date = Date()
    ) throws -> AlarmRecoveryJournalStopRoute {
        let state = try lockedRead()
        let stopAtEpochMillis = Self.epochMillis(stopAt)
        let exactTombstone = state.compactedTerminal(
            occurrenceToken: occurrenceToken,
            sourceKey: sourceKey,
            stopAtEpochMillis: stopAtEpochMillis,
            windowMillis: coalescingWindowMillis
        )
        let tokenlessRootDuplicate = retryAttempt == 0 ? state.compactedTerminal(
            occurrenceToken: nil,
            sourceKey: sourceKey,
            stopAtEpochMillis: stopAtEpochMillis,
            windowMillis: coalescingWindowMillis
        ) : nil
        if let tombstone = exactTombstone ?? tokenlessRootDuplicate {
            return .compactedTerminal(tombstone)
        }
        if let occurrenceToken,
           let attempt = state.chains.values.flatMap(\.attempts).first(where: {
               $0.nextOccurrenceToken == occurrenceToken
           }) {
            return attempt.sourceKey == sourceKey ? .liveAttempt(attempt) : .unknownRetry
        }
        if retryAttempt > 0 { return .unknownRetry }
        if let occurrenceToken,
           let occurrenceId = state.tokenIndex["token|\(occurrenceToken)"],
           let attempt = state.findAttempt(occurrenceId: occurrenceId) {
            return .liveAttempt(attempt)
        }
        if let attempt = state.findRootAttempt(
            sourceKey: sourceKey,
            stopAtEpochMillis: stopAtEpochMillis,
            windowMillis: coalescingWindowMillis
        ) {
            return .liveAttempt(attempt)
        }
        return .newRoot
    }

    func terminalCleanupSnapshots() throws -> [(chain: AlarmRecoveryChain, fingerprint: String)] {
        let state = try lockedRead()
        return try state.chains.values
            .filter { chain in
                guard let outcome = chain.terminalOutcome,
                      outcome != .active,
                      outcome != .expired else { return false }
                return chain.terminalAtEpochMillis != nil &&
                    chain.terminalOccurrenceId != nil &&
                    !chain.attempts.isEmpty
            }
            .sorted { $0.chainId < $1.chainId }
            .map { chain in
                (chain: chain, fingerprint: try Self.fingerprint(chain))
            }
    }

    func compactTerminalChain(
        chainId: String,
        expectedFingerprint: String
    ) throws -> AlarmRecoveryTerminalTombstone {
        try mutate { state in
            if let existing = state.terminalTombstones[chainId] {
                guard existing.chainFingerprint == expectedFingerprint else {
                    throw AlarmRecoveryJournalError.staleCleanupCandidate
                }
                return existing
            }
            guard let chain = state.chains[chainId],
                  let terminalOutcome = chain.terminalOutcome,
                  terminalOutcome != .active,
                  terminalOutcome != .expired,
                  let terminalOccurrenceId = chain.terminalOccurrenceId,
                  let terminalAtEpochMillis = chain.terminalAtEpochMillis,
                  let sourceKey = chain.attempts.first?.sourceKey,
                  !chain.attempts.isEmpty else {
                throw AlarmRecoveryJournalError.missingTerminalChain
            }
            guard try Self.fingerprint(chain) == expectedFingerprint else {
                throw AlarmRecoveryJournalError.staleCleanupCandidate
            }
            guard chain.terminalRecordAck && chain.attempts.allSatisfy(\.recordProjectionAck) else {
                throw AlarmRecoveryJournalError.unsettledProjection
            }
            guard chain.attempts.allSatisfy({ attempt in
                let ledger = attempt.currentReservation
                return ledger.status == .cancellationConfirmed &&
                    !ledger.cancellationRequired &&
                    (ledger.operationId == nil || ledger.scheduleCallSettled)
            }) else {
                throw AlarmRecoveryJournalError.unsettledReservation
            }

            let occurrenceIds = Set(chain.attempts.map(\.occurrenceId))
            var knownTokens = Set<String>()
            var retryReservationUUIDByToken: [String: String] = [:]
            var tokenlessRootStopAtEpochMillis: [Int64] = []
            var reservationUUIDs = Set<String>()
            for attempt in chain.attempts {
                knownTokens.insert(attempt.occurrenceId)
                if attempt.stopDedupKey.hasPrefix("token|") {
                    knownTokens.insert(String(attempt.stopDedupKey.dropFirst("token|".count)))
                } else if attempt.parentOccurrenceId == nil {
                    tokenlessRootStopAtEpochMillis.append(attempt.stopAtEpochMillis)
                }
                knownTokens.insert(attempt.nextOccurrenceToken)
                retryReservationUUIDByToken[attempt.nextOccurrenceToken] = attempt.nextReservationUUID
                reservationUUIDs.insert(attempt.nextReservationUUID)
            }
            let tombstone = AlarmRecoveryTerminalTombstone(
                chainId: chainId,
                chainFingerprint: expectedFingerprint,
                sourceKey: sourceKey,
                terminalOccurrenceId: terminalOccurrenceId,
                terminalOutcome: terminalOutcome,
                terminalAtEpochMillis: terminalAtEpochMillis,
                knownTokens: knownTokens.sorted(),
                retryReservationUUIDByToken: retryReservationUUIDByToken,
                tokenlessRootStopAtEpochMillis: tokenlessRootStopAtEpochMillis.sorted(),
                reservationUUIDs: reservationUUIDs.sorted(),
                cancellationRequiredReservationUUIDs: []
            )
            state.terminalTombstones[chainId] = tombstone
            state.tokenIndex = state.tokenIndex.filter { !occurrenceIds.contains($0.value) }
            state.rootWindowIndex = state.rootWindowIndex.filter { !occurrenceIds.contains($0.value) }
            state.chains.removeValue(forKey: chainId)
            return tombstone
        }
    }

    func tombstoneCancellationRequiredUUIDs() throws -> Set<String> {
        Set(try lockedRead().terminalTombstones.values.flatMap(\.cancellationRequiredReservationUUIDs))
    }

    func compactedReservationUUIDs() throws -> Set<String> {
        Set(try lockedRead().terminalTombstones.values.flatMap(\.reservationUUIDs))
    }

    func requireTombstoneCancellation(reservationUUID: String) throws {
        try mutate { state in
            guard let chainId = state.terminalTombstones.first(where: {
                $0.value.reservationUUIDs.contains(reservationUUID)
            })?.key,
            var tombstone = state.terminalTombstones[chainId] else { return }
            if !tombstone.cancellationRequiredReservationUUIDs.contains(reservationUUID) {
                tombstone.cancellationRequiredReservationUUIDs.append(reservationUUID)
                tombstone.cancellationRequiredReservationUUIDs.sort()
                state.terminalTombstones[chainId] = tombstone
            }
        }
    }

    func markTombstoneCancellationConfirmed(reservationUUID: String) throws {
        try mutate { state in
            guard let chainId = state.terminalTombstones.first(where: {
                $0.value.reservationUUIDs.contains(reservationUUID)
            })?.key,
            var tombstone = state.terminalTombstones[chainId] else { return }
            tombstone.cancellationRequiredReservationUUIDs.removeAll { $0 == reservationUUID }
            state.terminalTombstones[chainId] = tombstone
        }
    }

    func claimNextReservation(
        occurrenceId: String,
        operationId: String = UUID().uuidString,
        executionEpoch: String,
        now: Date = Date()
    ) throws -> AlarmRecoveryReservationClaim? {
        try mutate { state in
            let nowMillis = Self.epochMillis(now)
            guard let location = state.location(of: occurrenceId) else { return nil }
            var chain = state.chains[location.chainId]!
            var attempt = chain.attempts[location.attemptIndex]
            guard chain.terminalOutcome == nil,
                  attempt.outcome == .active else {
                return nil
            }
            guard attempt.expiresAtEpochMillis > nowMillis else {
                attempt.outcome = .expired
                chain.attempts[location.attemptIndex] = attempt
                state.chains[location.chainId] = chain
                return nil
            }
            guard attempt.currentReservation.status == .pending,
                  attempt.currentReservation.operationId == nil else {
                return nil
            }
            let generation = attempt.currentReservation.generation + 1
            attempt.currentReservation.status = .inFlight
            attempt.currentReservation.operationId = operationId
            attempt.currentReservation.generation = generation
            attempt.currentReservation.executionEpoch = executionEpoch
            attempt.currentReservation.scheduleCallSettled = false
            attempt.currentReservation.lastUpdatedEpochMillis = nowMillis
            chain.attempts[location.attemptIndex] = attempt
            state.chains[location.chainId] = chain
            return AlarmRecoveryReservationClaim(
                occurrenceId: occurrenceId,
                operationId: operationId,
                generation: generation,
                reservationUUID: attempt.nextReservationUUID,
                executionEpoch: executionEpoch
            )
        }
    }

    func completeReservation(
        claim: AlarmRecoveryReservationClaim,
        status: AlarmRecoveryReservationStatus,
        error: String? = nil,
        now: Date = Date()
    ) throws {
        try mutate { state in
            guard let location = state.location(of: claim.occurrenceId) else { return }
            var chain = state.chains[location.chainId]!
            var attempt = chain.attempts[location.attemptIndex]
            let nowMillis = Self.epochMillis(now)
            let matches = attempt.currentReservation.operationId == claim.operationId
                && attempt.currentReservation.generation == claim.generation
                && attempt.currentReservation.reservationUUID == claim.reservationUUID
                && attempt.currentReservation.executionEpoch == claim.executionEpoch
            guard matches else { throw AlarmRecoveryJournalError.staleReservationClaim }
            if chain.terminalOutcome != nil {
                attempt.currentReservation.lateScheduleAfterTerminal = status == .confirmed || status == .unknown
                attempt.currentReservation.cancellationRequired = true
                attempt.currentReservation.status = .cancellationRequired
                attempt.currentReservation.lastUpdatedEpochMillis = nowMillis
                attempt.currentReservation.lastError = error
                attempt.currentReservation.scheduleCallSettled = true
            } else {
                attempt.currentReservation.status = status
                attempt.currentReservation.lastUpdatedEpochMillis = nowMillis
                attempt.currentReservation.lastError = error
                attempt.currentReservation.scheduleCallSettled = true
            }
            chain.attempts[location.attemptIndex] = attempt
            state.chains[location.chainId] = chain
        }
    }

    func recordReservationPresence(
        reservationUUID: String,
        presence: AlarmRecoveryReservationStatus,
        error: String? = nil,
        now: Date = Date()
    ) throws {
        try mutate { state in
            guard let location = state.location(reservationUUID: reservationUUID) else { return }
            var chain = state.chains[location.chainId]!
            var attempt = chain.attempts[location.attemptIndex]
            let nowMillis = Self.epochMillis(now)
            if chain.terminalOutcome != nil || attempt.currentReservation.cancellationRequired {
                if presence == .confirmed {
                    attempt.currentReservation.status = .cancellationRequired
                    attempt.currentReservation.cancellationRequired = true
                    attempt.currentReservation.cancellationConfirmedAtEpochMillis = nil
                    attempt.currentReservation.lateScheduleAfterTerminal = true
                } else if presence == .absent || presence == .unknown {
                    attempt.currentReservation.status = attempt.currentReservation.cancellationRequired
                        ? .cancellationRequired
                        : attempt.currentReservation.status
                }
                attempt.currentReservation.lastError = error
                attempt.currentReservation.lastUpdatedEpochMillis = nowMillis
                chain.attempts[location.attemptIndex] = attempt
                state.chains[location.chainId] = chain
                return
            }
            switch presence {
            case .confirmed:
                attempt.currentReservation.status = .confirmed
            case .absent:
                if attempt.currentReservation.status == .pending {
                    attempt.currentReservation.status = .absent
                } else if attempt.currentReservation.status == .inFlight {
                    attempt.currentReservation.status = .unknown
                }
            case .unknown, .failed:
                attempt.currentReservation.status = presence
            default:
                break
            }
            attempt.currentReservation.lastError = error
            attempt.currentReservation.lastUpdatedEpochMillis = nowMillis
            chain.attempts[location.attemptIndex] = attempt
            state.chains[location.chainId] = chain
        }
    }

    func markCancellationConfirmed(
        reservationUUID: String,
        now: Date = Date()
    ) throws {
        try mutate { state in
            guard let location = state.location(reservationUUID: reservationUUID) else { return }
            var chain = state.chains[location.chainId]!
            var attempt = chain.attempts[location.attemptIndex]
            if attempt.currentReservation.operationId != nil,
               !attempt.currentReservation.scheduleCallSettled {
                attempt.currentReservation.status = .cancellationRequired
                attempt.currentReservation.cancellationRequired = true
                attempt.currentReservation.lastUpdatedEpochMillis = Self.epochMillis(now)
                chain.attempts[location.attemptIndex] = attempt
                state.chains[location.chainId] = chain
                return
            }
            attempt.currentReservation.status = .cancellationConfirmed
            attempt.currentReservation.cancellationRequired = false
            attempt.currentReservation.cancellationConfirmedAtEpochMillis = Self.epochMillis(now)
            chain.attempts[location.attemptIndex] = attempt
            state.chains[location.chainId] = chain
        }
    }

    func markTerminal(
        chainId: String,
        outcome: AlarmRecoveryAttemptOutcome,
        occurrenceId: String? = nil,
        now: Date = Date()
    ) throws {
        try mutate { state in
            guard var chain = state.chains[chainId],
                  chain.terminalOutcome == nil else { return }
            chain.terminalOccurrenceId = occurrenceId ?? chain.attempts.max(by: { $0.sequence < $1.sequence })?.occurrenceId
            chain.terminalOutcome = outcome
            chain.terminalAtEpochMillis = Self.epochMillis(now)
            for index in chain.attempts.indices {
                chain.attempts[index].outcome = outcome
                if chain.attempts[index].currentReservation.status != .cancellationConfirmed {
                    chain.attempts[index].currentReservation.cancellationRequired = true
                    chain.attempts[index].currentReservation.status = .cancellationRequired
                }
            }
            state.chains[chainId] = chain
        }
    }

    func ackMissionProjection(occurrenceId: String) throws {
        try setProjectionAck(occurrenceId: occurrenceId, mission: true)
    }

    func ackRecordProjection(occurrenceId: String) throws {
        try setProjectionAck(occurrenceId: occurrenceId, mission: false)
    }

    func ackTerminalRecord(chainId: String) throws {
        try mutate { state in
            guard var chain = state.chains[chainId] else { return }
            chain.terminalRecordAck = true
            state.chains[chainId] = chain
        }
    }

    func recordPresentation(
        stage: String, mode: String, canContinueInForeground: Bool,
        protectedDataAvailable: Bool, now: Date = Date()
    ) throws {
        try mutate { state in
            var observations = state.presentationObservations ?? []
            observations.append(AlarmRecoveryPresentationObservation(
                stage: stage, atEpochMillis: Self.epochMillis(now), mode: mode,
                canContinueInForeground: canContinueInForeground,
                protectedDataAvailable: protectedDataAvailable
            ))
            state.presentationObservations = Array(observations.suffix(32))
        }
    }

    func recoveryState() throws -> AlarmRecoveryState {
        try lockedRead()
    }

    func attemptsForRecovery() throws -> [AlarmRecoveryAttempt] {
        try lockedRead().chains.values
            .flatMap(\.attempts)
            .sorted { ($0.chainId, $0.sequence) < ($1.chainId, $1.sequence) }
    }

    func protectedReservationUUIDs() throws -> Set<String> {
        let state = try lockedRead()
        let attempts = state.chains.values.flatMap(\.attempts)
        let activeReservations = Set(attempts.compactMap { attempt in
            attempt.currentReservation.status == .cancellationConfirmed ? nil : attempt.nextReservationUUID
        })
        let tombstoneReservations = Set(state.terminalTombstones.values.flatMap(\.reservationUUIDs))
        return activeReservations.union(tombstoneReservations)
    }

    func importLegacy(
        occurrenceId: String,
        snapshot: AlarmRecoverySourceSnapshot,
        stopAt: Date,
        reservationUUID: String?,
        nextOccurrenceToken: String? = nil,
        expiresAtEpochMillis: Int64? = nil,
        sequence: Int = 0
    ) throws -> AlarmRecoveryAttempt {
        try mutate { state in
            if let existing = state.findAttempt(occurrenceId: occurrenceId) {
                return existing
            }
            if state.chains[occurrenceId] != nil {
                throw AlarmRecoveryJournalError.legacyConflict
            }
            let nowMillis = Self.epochMillis(stopAt)
            let sourceKey = AlarmRecoverySourceKey(
                sourceAlarmId: snapshot.sourceAlarmId,
                scheduleVersion: snapshot.scheduleVersion
            )
            let isKnownCompactedTerminal = state.compactedTerminal(
                occurrenceToken: occurrenceId,
                sourceKey: sourceKey,
                stopAtEpochMillis: nowMillis,
                windowMillis: coalescingWindowMillis
            ) != nil
            let isCompactedTokenlessRootDuplicate = sequence == 0 && state.compactedTerminal(
                occurrenceToken: nil,
                sourceKey: sourceKey,
                stopAtEpochMillis: nowMillis,
                windowMillis: coalescingWindowMillis
            ) != nil
            guard !isKnownCompactedTerminal && !isCompactedTokenlessRootDuplicate else {
                throw AlarmRecoveryJournalError.terminalChain
            }
            let ledger = AlarmRecoveryReservationLedger(
                reservationUUID: reservationUUID ?? uuidProvider(),
                status: reservationUUID == nil ? .unknown : .confirmed,
                operationId: nil,
                generation: 0,
                executionEpoch: nil,
                scheduleCallSettled: reservationUUID != nil,
                lastUpdatedEpochMillis: nowMillis,
                lastError: nil,
                cancellationRequired: false,
                cancellationConfirmedAtEpochMillis: nil,
                lateScheduleAfterTerminal: false
            )
            let attempt = AlarmRecoveryAttempt(
                occurrenceId: occurrenceId,
                chainId: occurrenceId,
                parentOccurrenceId: nil,
                sequence: sequence,
                sourceKey: sourceKey,
                snapshot: snapshot,
                stopDedupKey: "legacy|\(occurrenceId)",
                stopAtEpochMillis: nowMillis,
                scheduledAtEpochMillis: snapshot.scheduledAtEpochMillis,
                expiresAtEpochMillis: expiresAtEpochMillis ?? nowMillis + snapshot.limitSeconds * 1_000,
                nextReservationUUID: ledger.reservationUUID,
                nextOccurrenceToken: nextOccurrenceToken ?? uuidProvider(),
                currentReservation: ledger,
                missionProjectionAck: false,
                recordProjectionAck: false,
                outcome: .active
            )
            state.chains[occurrenceId] = AlarmRecoveryChain(
                chainId: occurrenceId,
                terminalOutcome: nil,
                terminalAtEpochMillis: nil,
                terminalRecordAck: false,
                attempts: [attempt]
            )
            return attempt
        }
    }

    private func setProjectionAck(occurrenceId: String, mission: Bool) throws {
        try mutate { state in
            guard let location = state.location(of: occurrenceId) else { return }
            var chain = state.chains[location.chainId]!
            if mission {
                chain.attempts[location.attemptIndex].missionProjectionAck = true
            } else {
                chain.attempts[location.attemptIndex].recordProjectionAck = true
            }
            state.chains[location.chainId] = chain
        }
    }

    private func mutate<T>(_ body: (inout AlarmRecoveryState) throws -> T) throws -> T {
        try withFileLock {
            var state = try readUnlocked()
            let result = try body(&state)
            try writeUnlocked(state)
            return result
        }
    }

    private func lockedRead() throws -> AlarmRecoveryState {
        try withFileLock {
            try readUnlocked()
        }
    }

    private func withFileLock<T>(_ body: () throws -> T) throws -> T {
        let directory = try directory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try applyProtectedFileAttributes(to: directory)
        let lockURL = directory.appendingPathComponent("alarm-recovery-journal.lock")
        let fd = open(lockURL.path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        if fd == -1 {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        defer { close(fd) }
        try applyProtectedFileAttributes(to: lockURL)
        if flock(fd, LOCK_EX) != 0 {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        defer { flock(fd, LOCK_UN) }
        return try body()
    }

    private func readUnlocked() throws -> AlarmRecoveryState {
        let url = try fileURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return .empty }
        let data = try Data(contentsOf: url)
        let state = try JSONDecoder().decode(AlarmRecoveryState.self, from: data)
        guard state.schemaVersion == AlarmRecoveryState.empty.schemaVersion else {
            throw AlarmRecoveryJournalError.unsupportedSchemaVersion
        }
        return state
    }

    private func writeUnlocked(_ state: AlarmRecoveryState) throws {
        let url = try fileURL()
        let directory = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try applyProtectedFileAttributes(to: directory)
        let tempURL = directory.appendingPathComponent(".\(url.lastPathComponent).\(UUID().uuidString).tmp")
        do {
            try writeDurably(JSONEncoder.stable.encode(state), to: tempURL)
            try applyProtectedFileAttributes(to: tempURL)
            if FileManager.default.fileExists(atPath: url.path) {
                _ = try FileManager.default.replaceItemAt(url, withItemAt: tempURL)
            } else {
                try FileManager.default.moveItem(at: tempURL, to: url)
            }
            try applyProtectedFileAttributes(to: url)
            try fsyncDirectory(directory)
        } catch {
            try? FileManager.default.removeItem(at: tempURL)
            throw error
        }
    }

    private func directory() throws -> URL {
        if let directoryURL { return directoryURL }
        return try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("Ringout", isDirectory: true)
    }

    private func fileURL() throws -> URL {
        try directory().appendingPathComponent("alarm-recovery-journal.json")
    }

    private func applyProtectedFileAttributes(to url: URL) throws {
        #if os(iOS)
        try FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
        #endif
    }

    private func writeDurably(_ data: Data, to url: URL) throws {
        let fd = open(url.path, O_CREAT | O_TRUNC | O_WRONLY, S_IRUSR | S_IWUSR)
        if fd == -1 {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        do {
            try data.withUnsafeBytes { rawBuffer in
                guard let baseAddress = rawBuffer.baseAddress else { return }
                var remaining = rawBuffer.count
                var pointer = baseAddress
                while remaining > 0 {
                    let written = write(fd, pointer, remaining)
                    if written == -1 {
                        throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
                    }
                    remaining -= written
                    pointer = pointer.advanced(by: written)
                }
            }
            if fsync(fd) != 0 {
                throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
            }
        } catch {
            close(fd)
            throw error
        }
        if close(fd) != 0 {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
    }

    private func fsyncDirectory(_ directory: URL) throws {
        let fd = open(directory.path, O_RDONLY)
        if fd == -1 {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        defer { close(fd) }
        if fsync(fd) != 0 {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
    }

    private static func epochMillis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1_000).rounded())
    }

    private static func fingerprint(_ chain: AlarmRecoveryChain) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let digest = SHA256.hash(data: try encoder.encode(chain))
        return "sha256:" + digest.map { String(format: "%02x", Int($0)) }.joined()
    }
}

private extension JSONEncoder {
    static var stable: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]
        return encoder
    }
}

private extension AlarmRecoveryState {
    func compactedTerminal(
        occurrenceToken: String?,
        sourceKey: AlarmRecoverySourceKey,
        stopAtEpochMillis: Int64,
        windowMillis: Int64
    ) -> AlarmRecoveryTerminalTombstone? {
        terminalTombstones.values.first { tombstone in
            if let occurrenceToken, tombstone.knownTokens.contains(occurrenceToken) {
                return true
            }
            guard occurrenceToken == nil, tombstone.sourceKey == sourceKey else { return false }
            return tombstone.tokenlessRootStopAtEpochMillis.contains {
                abs($0 - stopAtEpochMillis) <= windowMillis
            }
        }
    }

    func findAttempt(occurrenceId: String) -> AlarmRecoveryAttempt? {
        guard let location = location(of: occurrenceId) else { return nil }
        return chains[location.chainId]?.attempts[location.attemptIndex]
    }

    func findRootAttempt(
        sourceKey: AlarmRecoverySourceKey,
        stopAtEpochMillis: Int64,
        windowMillis: Int64
    ) -> AlarmRecoveryAttempt? {
        chains.values
            .flatMap(\.attempts)
            .filter { attempt in
                attempt.parentOccurrenceId == nil
                    && attempt.sourceKey == sourceKey
                    && attempt.stopDedupKey.hasPrefix("root-window|")
                    && abs(attempt.stopAtEpochMillis - stopAtEpochMillis) <= windowMillis
            }
            .sorted { $0.stopAtEpochMillis < $1.stopAtEpochMillis }
            .first
    }

    func location(of occurrenceId: String) -> (chainId: String, attemptIndex: Int)? {
        for (chainId, chain) in chains {
            if let index = chain.attempts.firstIndex(where: { $0.occurrenceId == occurrenceId }) {
                return (chainId, index)
            }
        }
        return nil
    }

    func location(reservationUUID: String) -> (chainId: String, attemptIndex: Int)? {
        for (chainId, chain) in chains {
            if let index = chain.attempts.firstIndex(where: { $0.nextReservationUUID == reservationUUID }) {
                return (chainId, index)
            }
        }
        return nil
    }
}
