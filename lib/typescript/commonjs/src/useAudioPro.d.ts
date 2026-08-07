import type { AudioProStore } from './internalStore';
import type { AudioProPlaybackErrorPayload, AudioProTrack } from './types';
import type { AudioProState } from './values';
export interface UseAudioProReturn {
    state: AudioProState;
    position: number;
    duration: number;
    playingTrack: AudioProTrack | null;
    playbackSpeed: number;
    volume: number;
    error: AudioProPlaybackErrorPayload | null;
}
/**
 * React hook for accessing the current state of the audio player.
 *
 * When used without arguments, it subscribes to all player state values.
 * To avoid unnecessary re-renders, you may pass a selector function to
 * subscribe only to the specific piece of state your component needs.
 */
export declare function useAudioPro(): UseAudioProReturn;
export declare function useAudioPro<T>(selector: (state: AudioProStore) => T, equalityFn?: (left: T, right: T) => boolean): T;
//# sourceMappingURL=useAudioPro.d.ts.map