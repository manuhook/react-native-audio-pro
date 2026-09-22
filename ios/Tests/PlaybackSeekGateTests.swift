// Exécution autonome : swiftc ios/PlaybackSeekGate.swift ios/Tests/PlaybackSeekGateTests.swift -o /tmp/audio-pro-seek-tests
@main
struct PlaybackSeekGateTests {
	static func main() {
		var gate = PlaybackSeekGate()
		let initial = gate.beginInitial()
		let user = gate.beginSeek()
		assert(!gate.complete(initial, completed: false), "Le seek initial annulé ne lance rien")
		assert(gate.isInitialPending, "La barrière reste levée jusqu'au seek utilisateur")
		assert(gate.complete(user, completed: true), "Le seek utilisateur relance le démarrage")
		assert(!gate.isInitialPending)

		let secondInitial = gate.beginInitial()
		let firstUser = gate.beginSeek()
		let lastUser = gate.beginSeek()
		assert(!gate.complete(firstUser, completed: true), "Une completion obsolète ne peut relancer")
		assert(!gate.complete(secondInitial, completed: false))
		assert(gate.complete(lastUser, completed: true))

		let stopped = gate.beginInitial()
		gate.reset()
		assert(!gate.complete(stopped, completed: true), "Stop/clear invalident la completion")
		assert(!gate.isInitialPending)

		let ordinary = gate.beginSeek()
		assert(!gate.complete(ordinary, completed: true), "Un seek ordinaire ne commande pas play")
		assert(!gate.isSeeking)
		let first = gate.beginSeek()
		let latest = gate.beginSeek()
		assert(gate.isSeeking, "Les mots attendent pendant un seek")
		_ = gate.complete(first, completed: true)
		assert(gate.isSeeking, "Une ancienne completion ne rouvre pas le transport")
		_ = gate.complete(latest, completed: false)
		assert(!gate.isSeeking, "Un seek ordinaire interrompu rend la main au transport natif")
		_ = gate.beginSeek()
		gate.reset()
		assert(!gate.isSeeking, "Le reset efface la barrière de seek")
		print("PlaybackSeekGate : 6 scénarios réussis")
	}
}
