// Un seek utilisateur peut remplacer celui du démarrage. Seule la dernière
// completion réussie lève la barrière avant le premier échantillon audible.
struct PlaybackSeekGate {
	private var generation: UInt64 = 0
	private(set) var isInitialPending = false

	mutating func beginInitial() -> UInt64 {
		isInitialPending = true
		return beginSeek()
	}

	mutating func beginSeek() -> UInt64 {
		generation &+= 1
		return generation
	}

	func isCurrent(_ token: UInt64) -> Bool { token == generation }

	mutating func complete(_ token: UInt64, completed: Bool) -> Bool {
		guard isCurrent(token), completed else { return false }
		let resumesInitialPlayback = isInitialPending
		isInitialPending = false
		return resumesInitialPlayback
	}

	mutating func reset() {
		generation &+= 1
		isInitialPending = false
	}
}
