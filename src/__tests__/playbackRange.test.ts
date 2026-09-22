import { NativeModules, Platform } from 'react-native';

import { AudioPro } from '../audioPro';
import { validateTrack } from '../utils';

const track = {
	id: 'extrait',
	url: 'https://example.com/audio.m3u8',
	title: 'Extrait',
	artwork: 'https://example.com/cover.jpg',
	startMs: 30000,
	endMs: 45000,
};

describe('Plages audio natives', () => {
	afterEach(() => {
		Platform.OS = 'ios';
		jest.restoreAllMocks();
	});

	it.each([
		{ startMs: -1 },
		{ startMs: Number.NaN },
		{ startMs: 1.5 },
		{ endMs: Number.POSITIVE_INFINITY },
		{ endMs: 30000 },
		{ endMs: 29000 },
	])('refuse une plage invalide %j', (range) => {
		expect(validateTrack({ ...track, ...range })).toBe(false);
	});

	it('transmet la plage et la reprise absolue au lecteur courant', () => {
		AudioPro.play(track, { startTimeMs: 35000 });
		expect(NativeModules.AudioPro.play).toHaveBeenCalledWith(
			track,
			expect.objectContaining({ startTimeMs: 35000 }),
		);
	});

	it('transmet les bornes de la piste pré-enfilée Android', () => {
		Platform.OS = 'android';
		AudioPro.setNextTrack(track);
		expect(NativeModules.AudioPro.setNextTrack).toHaveBeenCalledWith(track);
	});

	it('ne transforme jamais une plage invalide en média entier', () => {
		jest.spyOn(console, 'error').mockImplementation(() => {});
		Platform.OS = 'android';
		AudioPro.play({ ...track, endMs: 1000 });
		AudioPro.setNextTrack({ ...track, endMs: 1000 });
		expect(NativeModules.AudioPro.play).not.toHaveBeenCalled();
		expect(NativeModules.AudioPro.setNextTrack).not.toHaveBeenCalled();
	});
});
