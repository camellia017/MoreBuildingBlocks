#!/usr/bin/env node
/*
 * Scan the generated pane models for the uv windows their faces read, and for the places
 * where two models come to stand in the same plane over the same pixels reading different
 * windows - the coplanar contest that makes a pane flicker or lose a pixel.
 *
 * Usage
 *   node tools/scan-model-uv.mjs [options] [name filters...]
 *
 * Options
 *   --dir <path>          models directory (default: the generated block models)
 *   --match <text>        keep models whose name contains text (repeatable)
 *   --not <text>          drop models whose name contains text (repeatable)
 *   --templates           keep the templates the model provider declares, not their copies
 *   --provider <path>     the provider --templates reads the names from
 *   --plane <axis=value>  keep faces lying on that plane, e.g. z=8, x=8.001, y=8
 *   --uv <u1,v1,u2,v2>    keep faces whose window covers that window
 *   --faces               one line per face rather than one per model
 *   --clash               report same-plane overlaps that read different windows
 *   --tol <number>        plane distance still counted as one plane (default 0.002)
 *   --json                print what was found as JSON
 *   -h, --help
 *
 * Name filters are matched against the model file name without its .json suffix. With no
 * filter at all every model in the directory is read and only the totals are printed.
 *
 * Every face is placed on the plane its box side lies in - a north face on z=from.z, an
 * east face on x=to.x, a down face on y=from.y - and overlaps another when their footprints
 * on that plane share more than an edge. A contest is two such faces that read different
 * pixels: a different window, a mirror of one, or a full sprite against a window.
 */

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_DIR = 'src/main/generated/assets/morebuildingblocks/models/block';
const DEFAULT_PROVIDER = 'src/client/java/com/camelie/morebuildingblocks/client/datagen/MBModelProvider.java';

/** How each face stands on its box: the axis it faces down, the corner it lies on, its spans. */
const SIDES = {
	north: { axis: 'z', corner: 'from', spans: ['x', 'y'] },
	south: { axis: 'z', corner: 'to', spans: ['x', 'y'] },
	west: { axis: 'x', corner: 'from', spans: ['z', 'y'] },
	east: { axis: 'x', corner: 'to', spans: ['z', 'y'] },
	down: { axis: 'y', corner: 'from', spans: ['x', 'z'] },
	up: { axis: 'y', corner: 'to', spans: ['x', 'z'] },
};

const AXIS = { x: 0, y: 1, z: 2 };

function usage() {
	return readFileSync(fileURLToPath(import.meta.url), 'utf8')
		.split('\n')
		.slice(1, 30)
		.filter((line) => line.startsWith(' *'))
		.map((line) => line.slice(3))
		.join('\n');
}

function parseArgs(argv) {
	const options = {
		dir: path.join(ROOT, DEFAULT_DIR),
		provider: path.join(ROOT, DEFAULT_PROVIDER),
		match: [],
		not: [],
		templates: false,
		plane: null,
		uv: null,
		faces: false,
		clash: false,
		tol: 0.002,
		json: false,
	};

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
			case '--dir':
				options.dir = path.resolve(ROOT, value());
				break;
			case '--match':
				options.match.push(value());
				break;
			case '--not':
				options.not.push(value());
				break;
			case '--templates':
				options.templates = true;
				break;
			case '--provider':
				options.provider = path.resolve(ROOT, value());
				break;
			case '--plane': {
				const text = value();
				const at = text.indexOf('=');
				if (at < 0) throw new Error(`--plane wants axis=value, got ${text}`);
				const axis = text.slice(0, at);
				if (!(axis in AXIS)) throw new Error(`--plane wants x, y or z, got ${axis}`);
				options.plane = { axis, value: Number(text.slice(at + 1)) };
				break;
			}
			case '--uv': {
				const numbers = value().split(',').map(Number);
				if (numbers.length !== 4 || numbers.some((number) => !Number.isFinite(number))) {
					throw new Error('--uv wants four numbers: u1,v1,u2,v2');
				}
				options.uv = numbers;
				break;
			}
			case '--faces':
				options.faces = true;
				break;
			case '--clash':
				options.clash = true;
				break;
			case '--tol':
				options.tol = Number(value());
				break;
			case '--json':
				options.json = true;
				break;
			default:
				if (arg.startsWith('-')) throw new Error(`unknown option ${arg}`);
				options.match.push(arg);
		}
	}

	return options;
}

/** Every face of one model, on the plane it lies in and with the pixels it reads. */
function facesOf(name, model) {
	const found = [];

	(model.elements || []).forEach((element, index) => {
		const from = element.from || [0, 0, 0];
		const to = element.to || [16, 16, 16];

		for (const [direction, face] of Object.entries(element.faces || {})) {
			const side = SIDES[direction];
			if (!side) continue;

			const corner = side.corner === 'from' ? from : to;
			const [first, second] = side.spans;

			found.push({
				id: `${name}#${index}/${direction}`,
				model: name,
				element: index,
				direction,
				from,
				to,
				uv: face.uv ? [...face.uv] : null,
				rotation: face.rotation || 0,
				texture: face.texture || null,
				cullface: face.cullface || null,
				axis: side.axis,
				value: corner[AXIS[side.axis]],
				span: [
					[Math.min(from[AXIS[first]], to[AXIS[first]]), Math.max(from[AXIS[first]], to[AXIS[first]])],
					[Math.min(from[AXIS[second]], to[AXIS[second]]), Math.max(from[AXIS[second]], to[AXIS[second]])],
				],
			});
		}
	});

	return found;
}

/** The window as the pixels it covers, so a mirrored writing counts as the same pixels. */
function pixels(face) {
	if (!face.uv) return null;
	const u = [face.uv[0], face.uv[2]].sort((a, b) => a - b);
	const v = [face.uv[1], face.uv[3]].sort((a, b) => a - b);
	return `${u[0]},${v[0]},${u[1]},${v[1]}`;
}

function writes(face) {
	return face.uv ? face.uv.join(',') : 'sprite';
}

function covers(face, window) {
	if (!face.uv) return false;
	const u = [Math.min(face.uv[0], face.uv[2]), Math.max(face.uv[0], face.uv[2])];
	const v = [Math.min(face.uv[1], face.uv[3]), Math.max(face.uv[1], face.uv[3])];
	const [u1, v1, u2, v2] = window;
	return u[0] <= Math.min(u1, u2) && u[1] >= Math.max(u1, u2) && v[0] <= Math.min(v1, v2) && v[1] >= Math.max(v1, v2);
}

/** The rectangle two faces share on their plane, or null when they only touch. */
function overlap(one, other) {
	const x = Math.min(one.span[0][1], other.span[0][1]) - Math.max(one.span[0][0], other.span[0][0]);
	const y = Math.min(one.span[1][1], other.span[1][1]) - Math.max(one.span[1][0], other.span[1][0]);
	if (x <= 0 || y <= 0) return null;
	return [
		[Math.max(one.span[0][0], other.span[0][0]), Math.min(one.span[0][1], other.span[0][1])],
		[Math.max(one.span[1][0], other.span[1][0]), Math.min(one.span[1][1], other.span[1][1])],
	];
}

/** The names the provider declares: each template, its bars twin and its plain twin. */
function templateNames(provider) {
	const source = readFileSync(provider, 'utf8');
	const declared = new Set();

	for (const [, name] of source.matchAll(/new PaneTemplate\("([^"]+)"/g)) {
		declared.add(name);
		declared.add(`${name}_bars`);
		declared.add(`${name}_bars_plain`);
	}

	if (declared.size === 0) throw new Error(`no PaneTemplate declarations in ${provider}`);
	return declared;
}

function readModels(options) {
	const declared = options.templates ? templateNames(options.provider) : null;

	const names = readdirSync(options.dir)
		.filter((name) => name.endsWith('.json'))
		.map((name) => name.slice(0, -'.json'.length))
		.filter((name) => (declared ? declared.has(name) : true))
		.filter((name) => (options.match.length === 0 ? true : options.match.some((text) => name.includes(text))))
		.filter((name) => !options.not.some((text) => name.includes(text)))
		.sort();

	const faces = [];
	for (const name of names) {
		const model = JSON.parse(readFileSync(path.join(options.dir, `${name}.json`), 'utf8'));
		faces.push(...facesOf(name, model));
	}

	return { names, faces };
}

/** Every pair of faces that share a plane, cover the same pixels and read them differently. */
function clashes(faces, tol) {
	const buckets = new Map();
	const key = (face, shift) => `${face.axis}:${Math.round(face.value * 1000) + shift}`;

	for (const face of faces) {
		const at = key(face, 0);
		if (!buckets.has(at)) buckets.set(at, []);
		buckets.get(at).push(face);
	}

	const found = [];
	const seen = new Set();
	const shifts = [0, 1, -1, 2, -2];

	for (const face of faces) {
		for (const shift of shifts) {
			for (const other of buckets.get(key(face, shift)) || []) {
				if (other.id === face.id) continue;
				const pair = [face.id, other.id].sort().join(' ~ ');
				if (seen.has(pair)) continue;

				const distance = Math.abs(other.value - face.value);
				if (distance > tol) continue;

				const shared = overlap(face, other);
				if (!shared) continue;

				const sameModelSamePlane = face.model === other.model && distance === 0;
				if (sameModelSamePlane) continue;

				if (pixels(face) === pixels(other) && face.texture === other.texture) continue;

				seen.add(pair);
				found.push({ one: face, other, distance, shared });
			}
		}
	}

	found.sort((a, b) => a.distance - b.distance || a.one.value - b.one.value || a.one.id.localeCompare(b.one.id));
	return found;
}

function place(face) {
	return `${face.axis}=${face.value.toFixed(3)}  [${face.from.join(',')}] -> [${face.to.join(',')}]`;
}

function main() {
	const options = parseArgs(process.argv.slice(2));
	const { names, faces } = readModels(options);

	let kept = faces;
	if (options.plane) kept = kept.filter((face) => face.axis === options.plane.axis && Math.abs(face.value - options.plane.value) < 1e-9);
	if (options.uv) kept = kept.filter((face) => covers(face, options.uv));

	if (options.clash) {
		const found = clashes(kept, options.tol);
		if (options.json) {
			console.log(JSON.stringify({ models: names.length, faces: kept.length, clashes: found.length, found }, null, 2));
			return;
		}

		console.log(`${found.length} same-plane overlaps reading different pixels, of ${kept.length} faces in ${names.length} models`);
		for (const { one, other, distance, shared } of found) {
			const span = `${shared[0][0]}..${shared[0][1]} x ${shared[1][0]}..${shared[1][1]}`;
			console.log('');
			console.log(`  ${place(one)}  uv=${writes(one)}  ${one.texture || ''}  ${one.model}`);
			console.log(`  ${place(other)}  uv=${writes(other)}  ${other.texture || ''}  ${other.model}`);
			console.log(`  apart ${distance.toFixed(3)}  shared ${span}`);
		}
		return;
	}

	if (options.json) {
		console.log(JSON.stringify({ models: names.length, faces: kept.length, kept }, null, 2));
		return;
	}

	if (options.faces) {
		for (const face of kept) {
			console.log(`${face.model.padEnd(52)} ${face.direction.padEnd(5)} ${place(face).padEnd(46)} uv=${writes(face)}${face.rotation ? ` rot=${face.rotation}` : ''}${face.cullface ? ` cull=${face.cullface}` : ''}`);
		}
		console.log(`${kept.length} faces in ${names.length} models`);
		return;
	}

	const byModel = new Map();
	for (const face of kept) {
		if (!byModel.has(face.model)) byModel.set(face.model, []);
		byModel.get(face.model).push(face);
	}

	if (options.match.length === 0 && !options.templates) {
		console.log(`${kept.length} faces in ${byModel.size} of ${names.length} models`);
		console.log('name a model with --match, or keep the modelled set with --templates, to see them');
		return;
	}

	for (const [model, own] of byModel) {
		const windows = [...new Set(own.map((face) => writes(face)))].sort();
		console.log(`${model}  ${own.length} faces  uv=${windows.join(' ')}`);
	}
	console.log(`${kept.length} faces in ${byModel.size} of ${names.length} models`);
}

main();
