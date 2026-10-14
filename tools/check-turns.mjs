#!/usr/bin/env node
/*
 * Check the generated pane models against the rules the model provider keeps, so a change
 * to the generator shows up here before it shows up in the game.
 *
 * Rules
 *   - every template has the bars twin the client asks for by name, and its nine bars copies;
 *   - every template the panes read has its own model and its seventeen pane copies;
 *   - the templates the bars alone read - the pixels of the crossing - have neither;
 *   - a template the turn touches has a plain twin, and that twin has its nine copies too;
 *   - every crossing pixel is the single-pixel wall it is meant to be: two elements, one
 *     face each, standing on the two sides of one plane and reading the same pixels of the
 *     bar mirrored.
 *
 * Usage
 *   node tools/check-turns.mjs [options]
 *
 * Options
 *   --provider <path>  the model provider to read the templates from
 *   --dir <path>       models directory (default: the generated block models)
 *   --readings         also list what each turn family model reads on the centre plane
 *   --json             print what was found as JSON
 *   -h, --help
 *
 * Exit code 1 when a rule fails, 0 when every rule holds.
 */

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_PROVIDER = 'src/client/java/com/camelie/morebuildingblocks/client/datagen/MBModelProvider.java';
const DEFAULT_DIR = 'src/main/generated/assets/morebuildingblocks/models/block';

/** The nine blocks IronBarsBlock covers whose path has no _pane: iron and the eight coppers. */
const BARS = [
	'iron_bars',
	'copper_bars',
	'exposed_copper_bars',
	'weathered_copper_bars',
	'oxidized_copper_bars',
	'waxed_copper_bars',
	'waxed_exposed_copper_bars',
	'waxed_weathered_copper_bars',
	'waxed_oxidized_copper_bars',
];

/** The seventeen the panes read: plain glass and the sixteen colours. */
const COLOURS = [
	'white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
	'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black',
];
const PANES = ['glass_pane', ...COLOURS.map((colour) => `${colour}_stained_glass_pane`)];

const OPPOSITE = { north: 'south', south: 'north', west: 'east', east: 'west', down: 'up', up: 'down' };
const ACROSS = { north: 'x', south: 'x', west: 'z', east: 'z', down: 'x', up: 'z' };
const AXIS = { x: 0, y: 1, z: 2 };

function usage() {
	return readFileSync(fileURLToPath(import.meta.url), 'utf8')
		.split('\n')
		.slice(1, 26)
		.filter((line) => line.startsWith(' *'))
		.map((line) => line.slice(3))
		.join('\n');
}

function parseArgs(argv) {
	const options = { provider: path.join(ROOT, DEFAULT_PROVIDER), dir: path.join(ROOT, DEFAULT_DIR), readings: false, json: false };

	for (let i = 0; i < argv.length; i += 1) {
		const arg = argv[i];
		const value = () => {
			i += 1;
			if (i >= argv.length) throw new Error(`${arg} needs a value`);
			return argv[i];
		};

		switch (arg) {
			case '-h':
			case '--help':
				console.log(usage());
				process.exit(0);
			case '--provider':
				options.provider = path.resolve(ROOT, value());
				break;
			case '--dir':
				options.dir = path.resolve(ROOT, value());
				break;
			case '--readings':
				options.readings = true;
				break;
			case '--json':
				options.json = true;
				break;
			default:
				throw new Error(`unknown option ${arg}`);
		}
	}

	return options;
}

/** The templates the provider declares, in the order it declares them. */
function readProvider(source) {
	const templates = [...source.matchAll(/new PaneTemplate\("([A-Za-z0-9_]+)"/g)].map((match) => match[1]);

	const block = source.match(/CROSSING_PIXELS = List\.of\(([\s\S]*?)\);/);
	const pixels = block ? [...block[1].matchAll(/"([a-z0-9_]+)"/g)].map((match) => match[1]) : [];

	return { templates, pixels };
}

function window(face) {
	return face.uv ? face.uv.join(',') : 'sprite';
}

function pixelWindow(face) {
	if (!face.uv) return null;
	const u = [face.uv[0], face.uv[2]].sort((a, b) => a - b);
	const v = [face.uv[1], face.uv[3]].sort((a, b) => a - b);
	return `${u[0]},${v[0]},${u[1]},${v[1]}`;
}

/** The plane a face lies in and the pixels it reads, for the crossing pixels and the readings. */
function planeOf(direction, element) {
	const from = element.from || [0, 0, 0];
	const to = element.to || [16, 16, 16];
	const axis = direction === 'north' || direction === 'south' ? 'z' : direction === 'west' || direction === 'east' ? 'x' : 'y';
	const corner = direction === 'north' || direction === 'west' || direction === 'down' ? from : to;
	return { axis, value: corner[AXIS[axis]] };
}

/** Every rule that does not hold, as one line each. */
function check(options, provider, files) {
	const problems = [];
	const notes = [];
	const { templates, pixels } = provider;

	if (templates.length === 0) problems.push('no PaneTemplate found in the provider');
	if (pixels.length === 0) problems.push('no CROSSING_PIXELS found in the provider');

	for (const name of pixels) {
		if (!templates.includes(name)) problems.push(`${name} is a crossing pixel but not a template`);
	}

	for (const template of templates) {
		const barsOnly = pixels.includes(template);
		const want = [`${template}_bars.json`];

		if (barsOnly) {
			if (files.has(`${template}.json`)) problems.push(`${template}.json exists though only the bars read it`);
			for (const pane of PANES) {
				if (files.has(`${template}_${pane}.json`)) problems.push(`${template}_${pane}.json exists though only the bars read it`);
			}
		} else {
			want.push(`${template}.json`);
			for (const pane of PANES) want.push(`${template}_${pane}.json`);
		}

		for (const bar of BARS) want.push(`${template}_${bar}.json`);

		for (const file of want) {
			if (!files.has(file)) problems.push(`${file} is missing`);
		}

		const plain = files.has(`${template}_bars_plain.json`);
		if (plain) notes.push(`${template} turns, so it has a plain twin`);
		for (const bar of BARS) {
			const copy = files.has(`${template}_bars_plain_${bar}.json`);
			if (copy !== plain) problems.push(`${template}_bars_plain_${bar}.json is ${copy ? 'there' : 'missing'} while the plain twin is ${plain ? 'there' : 'missing'}`);
		}
	}

	const readings = [];
	for (const template of templates) {
		const file = `${template}_bars.json`;
		if (!files.has(file)) continue;

		const model = JSON.parse(readFileSync(path.join(options.dir, file), 'utf8'));
		const elements = model.elements || [];

		for (const element of elements) {
			for (const [direction, face] of Object.entries(element.faces || {})) {
				const { axis, value } = planeOf(direction, element);
				if ((axis === 'x' || axis === 'z') && Math.abs(value - 8) <= 0.002) {
					readings.push({ template, direction, axis, value, uv: window(face), texture: face.texture || null });
				}
			}
		}

		if (!pixels.includes(template)) continue;

		if (elements.length !== 2) {
			problems.push(`${file} has ${elements.length} elements, not the two sides of its plane`);
			continue;
		}

		const entries = elements.map((element) => Object.entries(element.faces || {}));
		if (entries.some((faces) => faces.length !== 1)) {
			problems.push(`${file} carries ${entries.map((faces) => faces.length).join('/')} faces per element, not one each`);
			continue;
		}

		const [one, other] = entries.map((faces, index) => ({ direction: faces[0][0], face: faces[0][1], element: elements[index] }));

		if (OPPOSITE[one.direction] !== other.direction) {
			problems.push(`${file} faces ${one.direction} and ${other.direction}, not the two sides of one plane`);
		}

		const planes = [one, other].map((entry) => planeOf(entry.direction, entry.element));
		const values = planes.map((plane) => plane.value).sort((a, b) => a - b);
		if (planes[0].axis !== planes[1].axis) problems.push(`${file} has its two sides on different axes`);
		if (Math.abs(values[1] - values[0] - 0.001) > 1e-9) problems.push(`${file} holds its two sides ${(values[1] - values[0]).toFixed(3)} apart, not a thousandth`);

		for (const entry of [one, other]) {
			const across = ACROSS[entry.direction];
			const from = entry.element.from || [0, 0, 0];
			const to = entry.element.to || [16, 16, 16];
			const wide = Math.abs(to[AXIS[across]] - from[AXIS[across]]);
			const tall = Math.abs(to[1] - from[1]);
			if (wide !== 1 || tall !== 1) problems.push(`${file} draws a ${wide}x${tall} pixel, not one pixel`);
		}

		const [first, second] = [one, other].map((entry) => pixelWindow(entry.face));
		if (first !== second) problems.push(`${file} reads ${window(one.face)} against ${window(other.face)}, not the same pixels mirrored`);
		if (one.face.texture !== other.face.texture) problems.push(`${file} reads two sprites, ${one.face.texture} and ${other.face.texture}`);
	}

	return { problems, notes, readings, templates, pixels };
}

function main() {
	const options = parseArgs(process.argv.slice(2));
	const provider = readProvider(readFileSync(options.provider, 'utf8'));
	const files = new Set(readdirSync(options.dir).filter((name) => name.endsWith('.json')));
	const { problems, notes, readings, templates, pixels } = check(options, provider, files);

	if (options.json) {
		console.log(JSON.stringify({ templates: templates.length, crossingPixels: pixels.length, files: files.size, problems, notes, readings }, null, 2));
		process.exit(problems.length === 0 ? 0 : 1);
	}

	if (options.readings) {
		console.log(`${readings.length} faces on the centre plane:`);
		for (const reading of readings) {
			console.log(`  ${reading.template.padEnd(38)} ${reading.direction.padEnd(5)} ${reading.axis}=${reading.value.toFixed(3).padEnd(7)} uv=${reading.uv}  ${reading.texture || ''}`);
		}
		console.log('');
	}

	if (notes.length > 0) {
		console.log(`${notes.length} templates turn in the level plane and carry a plain twin:`);
		const names = notes.map((note) => note.split(' ')[0]);
		for (let i = 0; i < names.length; i += 4) {
			console.log(`  ${names.slice(i, i + 4).map((name) => name.padEnd(32)).join('')}`);
		}
		console.log('');
	}

	console.log(`${templates.length} templates, ${pixels.length} crossing pixels, ${files.size} models in the directory`);

	if (problems.length === 0) {
		console.log('every rule holds');
		return;
	}

	console.log(`${problems.length} rules broken:`);
	for (const problem of problems) console.log(`  ${problem}`);
	process.exit(1);
}

main();
