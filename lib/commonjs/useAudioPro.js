"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.useAudioPro = useAudioPro;
var _shallow = require("zustand/shallow");
var _internalStore = require("./internalStore.js");
const selectAll = state => ({
  state: state.playerState,
  position: state.position,
  duration: state.duration,
  playingTrack: state.trackPlaying,
  playbackSpeed: state.playbackSpeed,
  volume: state.volume,
  error: state.error
});
const select = _internalStore.internalStore;

/**
 * React hook for accessing the current state of the audio player.
 *
 * When used without arguments, it subscribes to all player state values.
 * To avoid unnecessary re-renders, you may pass a selector function to
 * subscribe only to the specific piece of state your component needs.
 */

function useAudioPro(selector, equalityFn) {
  const selectAllWithShallow = (0, _shallow.useShallow)(selectAll);
  if (selector) {
    return select(selector, equalityFn);
  }
  return (0, _internalStore.internalStore)(selectAllWithShallow);
}
//# sourceMappingURL=useAudioPro.js.map