<#
    render-iso-icons.ps1 -- offline isometric block-icon composer.

    Reads vanilla + mod model JSON (blockstates, models, item definitions) and
    texture PNGs (from a client jar plus the Loom-generated resource tree),
    rasterises each block model the way Minecraft renders it in the GUI slot
    (display.gui = rotation [30, 225, 0], scale 0.625) and writes one
    transparent PNG per block.

    No game process, no mod jar: the GUI transform is exactly the
    "display": { "gui": { "rotation": [30, 225, 0], "scale": [0.625, ...] } }
    block that assets/minecraft/models/block/block.json carries, applied
    orthographically. Faces whose outward normal is up / +X / +Z face the
    camera; anything else (slopes, cut corners) is skipped, which is why a
    stair icon comes out as its three camera-facing rectangles.

    Usage:
        powershell -NoProfile -ExecutionPolicy Bypass -File tools\render-iso-icons.ps1 `
            -MinecraftJar C:\path\to\minecraft-client-only.jar `
            -Output build\iso-icons

    Texture lookup order per texture id ("ns:path"):
      1. build\resources\main\assets\<ns>\textures\<path>.png   (Loom-generated)
      2. <ns>-ns.zip / minecraft jar  assets\<ns>\textures\<path>.png
      3. src\main\resources\assets\<ns>\textures\<path>.png
#>
[CmdletBinding()]
param(
    [string] $MinecraftJar = '',
    [string] $Output = 'build\iso-icons',
    [int]    $Size = 32,
    [int]    $SuperSample = 8,
    [string[]] $Filter = @(),
    [switch] $ListOnly,
    [switch] $Diag,
    # dot-source this script with -NoRun to get the helper functions without
    # rendering anything (used by build\iso-probe.ps1 while debugging)
    [switch] $NoRun
)

$script:Diag = $Diag

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$generated = Join-Path $root 'src\main\generated\assets'
$srcAssets = Join-Path $root 'src\main\resources\assets'
$loomAssets = Join-Path $root 'build\resources\main\assets'
$modNs = 'morebuildingblocks'

if (-not $MinecraftJar) {
    $cand = Get-ChildItem 'C:\Users\*\AppData\Roaming\.minecraft\versions' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending
    foreach ($c in $cand) {
        $j = Get-ChildItem (Join-Path $c.FullName '*.jar') -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
        if ($j) { $MinecraftJar = $j.FullName; break }
    }
}
$jarPath = $null
if ($MinecraftJar -and (Test-Path -LiteralPath $MinecraftJar)) { $jarPath = (Resolve-Path -LiteralPath $MinecraftJar).Path }
else {
    $loom = Get-ChildItem 'C:\Users\*\AppData\Local\fabric-loom\*\minecraft-client-only.jar',
                           'C:\Users\*\.gradle\caches\fabric-loom\*\minecraft-client-only.jar' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($loom) { $jarPath = $loom.FullName }
}
if (-not $jarPath) { throw 'no client jar found; pass -MinecraftJar <path to minecraft-client-only.jar>' }
$jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
Write-Host "jar   : $jarPath"

$outDir = Join-Path $root $Output
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

# ---------------------------------------------------------------- resource io

function Read-Json([string] $path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $null }
    try { return (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json) }
    catch {
        if ($script:Diag) { Write-Host "DBG   BAD JSON $path : $($_.Exception.Message)" }
        return $null
    }
}

function Split-Id([string] $id) {
    if ($id -match ':') { return ,@($id.Split(':', 2)) }
    # a bare path is always in the minecraft namespace (model parents are
    # written as "block/slab", so this branch matters)
    return ,@('minecraft', $id)
}

function Read-JsonResource([string] $ns, [string] $path) {
    $local = Join-Path $generated "$ns\$path"
    if ($script:Diag) { Write-Host "DBG   read $local exists=$(Test-Path -LiteralPath $local)" }
    if (Test-Path -LiteralPath $local) { return (Read-Json $local) }
    $local = Join-Path $srcAssets "$path"
    if ($ns -eq $modNs -and (Test-Path -LiteralPath $local)) { return (Read-Json $local) }
    $entryName = "assets/$ns/$path"
    $jar = $script:jar
    $e = $jar.GetEntry($entryName)
    if (-not $e) { return $null }
    $sr = New-Object System.IO.StreamReader($e.Open(), [System.Text.Encoding]::UTF8)
    try { $text = $sr.ReadToEnd() } finally { $sr.Close() }
    try { return ($text | ConvertFrom-Json) }
    catch {
        if ($script:Diag) { Write-Host "DBG   BAD JAR JSON $entryName : $($_.Exception.Message)" }
        return $null
    }
}

$script:texCache = @{}

function Read-Texture([string] $id) {
    $key = $id.ToLowerInvariant()
    if ($script:texCache.ContainsKey($key)) { return $script:texCache[$key] }
    $parts = Split-Id $key
    $ns = $parts[0]; $path = $parts[1]
    $rel = "$ns\textures\$($path -replace '/', '\').png"
    $file = $null
    $a = Join-Path $loomAssets $rel
    if (Test-Path -LiteralPath $a) { $file = $a }
    if (-not $file) { $b = Join-Path $srcAssets $rel; if (Test-Path -LiteralPath $b) { $file = $b } }
    $bmp = $null
    if ($file) { $bmp = [System.Drawing.Bitmap]::FromFile($file) }
    else {
        $e = $script:jar.GetEntry("assets/$ns/textures/$path.png")
        if ($e) {
            $ms = New-Object System.IO.MemoryStream
            $s = $e.Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
            $bmp = [System.Drawing.Bitmap]::FromStream($ms)
        }
    }
    $script:texCache[$key] = $bmp
    return $bmp
}

# ------------------------------------------------------------------- models

function Resolve-Model([string] $id, [hashtable] $seen) {
    if (-not $seen) { $seen = @{} }
    if ($seen.ContainsKey($id)) { return $null }
    $seen[$id] = $true
    $parts = Split-Id $id
    $json = Read-JsonResource $parts[0] "models\$($parts[1]).json"
    if (-not $json) { return $null }
    $out = [ordered]@{ textures = @{}; elements = @() }
    if ($json.parent) {
        $parent = Resolve-Model $json.parent $seen
        if ($parent) {
            foreach ($k in $parent.textures.Keys) { $out.textures[$k] = $parent.textures[$k] }
            $out.elements = $parent.elements
        }
    }
    if ($json.elements) { $out.elements = @($json.elements) }
    if ($json.textures) {
        foreach ($p in $json.textures.PSObject.Properties) { $out.textures[$p.Name] = $p.Value }
    }
    if ($script:Diag) {
        Write-Host ("DBG model $id parent=[" + $json.parent + "] elements=" + $out.elements.Count + " textures=" + $out.textures.Count)
    }
    return [pscustomobject]@{ elements = $out.elements; textures = $out.textures; parentId = $json.parent }
}

# Resolve a model, falling back to the vanilla copy from the client jar whenever
# a local (Loom-generated) copy of the same id cannot be read -- e.g. an empty
# build\resources\main\assets\minecraft directory shadowing the vanilla model.
function Resolve-ModelSafe([string] $id) {
    $resolved = Resolve-Model $id @{}
    if ($resolved -and $resolved.elements.Count -gt 0) { return $resolved }
    $rebuilt = Rebuild-ModelChain $id
    if ($rebuilt -and $rebuilt.elements.Count -gt 0) {
        if ($script:Diag) { Write-Host "DBG   rebuilt $id from vanilla chain: $($rebuilt.chain)" }
        return $rebuilt
    }
    return $resolved
}

function Get-VanillaModelJson([string] $namespace, [string] $path) {
    $entryName = "assets/$namespace/$path"
    $entry = $script:jar.GetEntry($entryName)
    if ($script:Diag) { Write-Host "DBG   vanilla $entryName -> $(if ($entry) { 'FOUND' } else { 'missing' })" }
    if (-not $entry) { return $null }
    $sr = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
    try { return ($sr.ReadToEnd() | ConvertFrom-Json) } finally { $sr.Close() }
}

# Walk a model's "parent" chain without Resolve-Model's recursion guard. This
# exists only for the case where the recursion in Resolve-Model cannot be
# trusted (see Resolve-ModelSafe): the chain starts at the model itself, so the
# root is read through Read-JsonResource (local tree first, then the jar) while
# every parent is read from its own namespace -- which for this mod's models is
# the vanilla namespace written as "minecraft:block/slab" or the bare
# "block/slab" that Split-Id maps to minecraft.
function Rebuild-ModelChain([string] $startId) {
    $entries = @()
    $id = $startId
    for ($i = 0; $i -lt 32; $i++) {
        $parts = Split-Id $id
        $json = if ($i -eq 0) {
            Read-JsonResource $parts[0] "models\$($parts[1]).json"
        } else {
            Get-VanillaModelJson $parts[0] "models/$($parts[1]).json"
        }
        if (-not $json) { break }
        $entries += [pscustomobject]@{ id = $id; json = $json }
        if ($json.elements) { break }
        if (-not $json.parent) { break }
        $id = $json.parent
    }
    $root = $null
    for ($i = $entries.Count - 1; $i -ge 0; $i--) {
        if ($entries[$i].json.elements) { $root = $entries[$i]; break }
    }
    if (-not $root) { return $null }
    $textures = @{}
    # iterate root -> child so a child's own texture map overrides the inherited one
    for ($i = $entries.Count - 1; $i -ge 0; $i--) {
        if (-not $entries[$i].json.textures) { continue }
        foreach ($p in $entries[$i].json.textures.PSObject.Properties) { $textures[$p.Name] = $p.Value }
    }
    $names = @($entries | ForEach-Object { $_.id })
    return [pscustomobject]@{
        elements = @($root.json.elements)
        textures = $textures
        parentId = $null
        chain    = ($names -join ' <- ')
    }
}

function Resolve-TextureRef([string] $ref, [hashtable] $textures, [int] $depth) {
    if ($depth -gt 16) { return $null }
    if (-not $ref) { return $null }
    if ($ref.StartsWith('#')) {
        $key = $ref.Substring(1)
        if (-not $textures.ContainsKey($key)) { return $null }
        return (Resolve-TextureRef $textures[$key] $textures ($depth + 1))
    }
    return $ref
}

# ---------------------------------------------------------- blockstate pick

function Test-ModelExists([string] $modelId) {
    $parts = Split-Id $modelId
    $local = Join-Path $generated "$($parts[0])\models\$($parts[1]).json"
    if (Test-Path -LiteralPath $local) { return $true }
    return ($null -ne $script:jar.GetEntry("assets/$($parts[0])/models/$($parts[1]).json"))
}

function Get-PropertyValue($obj, [string] $key) {
    if (-not $obj) { return $null }
    foreach ($p in $obj.PSObject.Properties) { if ($p.Name -ieq $key) { return $p.Value } }
    return $null
}

function Test-WhenWanted($when) {
    foreach ($p in $when.PSObject.Properties) {
        $want = @($p.Value) -join '|'
        switch -Regex ($p.Name) {
            'facing' { if ($want -notmatch 'south|east') { return $false } }
            'type'   { if ($want -notmatch 'bottom') { return $false } }
            'half'   { if ($want -notmatch 'bottom') { return $false } }
            'axis'   { if ($want -notmatch 'y') { return $false } }
            'shape'  { if ($want -notmatch 'straight') { return $false } }
            'waterlogged' { if ($want -match 'true') { return $false } }
            'level'  { return $false }
            default  {
                # a true/false state flag: take the shape, i.e. only the "true" side
                if ($want -notmatch 'true') { return $false }
            }
        }
    }
    return $true
}

function Get-BlockModelIds([string] $block) {
    $parts = Split-Id $block
    $blockName = $parts[1]
    $iconId = "$($parts[0]):block/$($blockName)_icon"
    if (Test-ModelExists $iconId) { return @($iconId) }
    $bs = Read-JsonResource $parts[0] "blockstates\$($blockName).json"
    if (-not $bs) { return @() }
    $ids = @()
    if ($bs.variants) {
        $names = @($bs.variants.PSObject.Properties.Name)
        if ($names.Count -gt 0) {
            # score every variant against the state we want to show:
            # facing south, upper half = bottom, full (non-vertical) slab, axis y
            $best = $null; $bestScore = [int]::MinValue; $bestIdx = 0; $idx = 0
            foreach ($n in $names) {
                $score = 0
                foreach ($kv in ($n -split ',')) {
                    $bits = $kv -split '=', 2
                    if ($bits.Count -ne 2) { continue }
                    $k = $bits[0].Trim(); $v = $bits[1].Trim()
                    switch -Regex ($k) {
                        'facing' { if ($v -eq 'south') { $score += 2 } elseif ($v -eq 'east') { $score += 1 } else { $score -= 4 } }
                        'type'   { if ($v -eq 'bottom') { $score += 3 } elseif ($v -eq 'top') { $score -= 3 } elseif ($v -eq 'double') { $score -= 5 } }
                        'half'   { if ($v -eq 'bottom') { $score += 3 } else { $score -= 3 } }
                        'axis'   { if ($v -eq 'y') { $score += 2 } elseif ($v -eq 'x') { $score += 1 } }
                        'vertical' { if ($v -eq 'true') { $score -= 4 } }
                        'waterlogged' { if ($v -eq 'true') { $score -= 8 } }
                        'north|south|east|west|up|down' { if ($v -eq 'true') { $score += 1 } else { $score -= 1 } }
                        default  { }
                    }
                }
                if ($score -gt $bestScore) { $bestScore = $score; $best = $n; $bestIdx = $idx }
                $idx++
            }
            if ($null -eq $best) { $best = $names[0] }
            $v = Get-PropertyValue $bs.variants $best
            foreach ($a in @($v)) { if ($a.model) { $ids += $a.model } }
        }
    }
    elseif ($bs.multipart) {
        foreach ($part in @($bs.multipart)) {
            if (-not (Test-WhenWanted $part.when)) { continue }
            foreach ($a in @($part.apply)) { if ($a.model) { $ids += $a.model } }
        }
    }
    return $ids
}

# ------------------------------------------------------------------ raster

function New-Rgba([int] $w, [int] $h) { return New-Object 'int[]' ($w * $h) }

function Draw-Element($el, [hashtable] $textures, [int[]] $buf, [int] $dim, [int] $ss, [double] $scale, [double[]] $org, [double] $us = 16.0) {
    # from/to are in the model's own units; scale them into the 16-unit block
    # space the projection expects, then restore the model's own offset. Keep a
    # quarter of a block unit of precision, or a thin element collapses to zero
    # width at low supersampling.
    $q = $ss / 4.0
    $x0 = [int][math]::Round(([double]$el.from[0] * $scale + $org[0]) * $q)
    $x1 = [int][math]::Round(([double]$el.to[0] * $scale + $org[0]) * $q)
    $y0 = [int][math]::Round(([double]$el.from[1] * $scale + $org[1]) * $q)
    $y1 = [int][math]::Round(([double]$el.to[1] * $scale + $org[1]) * $q)
    $z0 = [int][math]::Round(([double]$el.from[2] * $scale + $org[2]) * $q)
    $z1 = [int][math]::Round(([double]$el.to[2] * $scale + $org[2]) * $q)
    if ($x1 -le $x0 -or $y1 -le $y0 -or $z1 -le $z0) { return }
    if ($script:Diag) { Write-Host "DBG   element box x=$x0..$x1 y=$y0..$y1 z=$z0..$z1 (scale=$scale org=$($org -join ',') us=$us)" }
    # the three faces a +X/+Y/+Z camera sees, painted in back-to-front order
    foreach ($faceName in @('up', 'east', 'south')) {
        $face = $el.faces.$faceName
        if (-not $face) { continue }
        $tid = Resolve-TextureRef $face.texture $textures 0
        if (-not $tid) { continue }
        $tex = Read-Texture $tid
        if (-not $tex) { Write-Warning "missing texture $tid"; continue }
        if ($face.uv) { $u0 = [double]$face.uv[0]; $v0 = [double]$face.uv[1]; $u1 = [double]$face.uv[2]; $v1 = [double]$face.uv[3] }
        else { $u0 = 0.0; $v0 = 0.0; $u1 = 16.0; $v1 = 16.0 }
        $du = ($u1 - $u0) / 16.0
        $dv = ($v1 - $v0) / 16.0
        # Model box -> icon: a face is projected onto the $us-wide block space,
        # where x and z each span 0..$us and y runs 0..$us. Coordinates arriving
        # here are in quarter block units, so the scale is $ss/4 per unit.
        $boxSize = $us * $q
        # The model's own bounding box is centred on that space, so a full cube
        # fills the icon and a shorter block sits centred in the same footprint
        # (a bottom slab covers the lower half, like the vanilla icon).
        $yOff = 0.0
        # Minecraft's own face shading, as Direction.getShade(): the up face is
    # unshaded, the two faces pointing at the camera sit in shadow.
    $shade = switch ($faceName) { 'up' { 1.0 } 'east' { 0.6 } 'south' { 0.8 } default { 0.8 } }
    $fsh = [int][math]::Round($shade * 256)
    # tinted lookup for this face, so the per-pixel work stays an array read
    $tw = [int]$tex.Width; $th = [int]$tex.Height
    $lut = New-Object 'int[]' ($tw * $th)
    for ($i = 0; $i -lt $lut.Length; $i++) {
        $cc = $tex.GetPixel(($i % $tw), [int]([math]::Floor($i / $tw)))
        if ($cc.A -eq 0) { continue }
        $lut[$i] = ([int]$cc.A -shl 24) -bor ([int](($cc.R * $fsh) -shr 8) -shl 16) -bor ([int](($cc.G * $fsh) -shr 8) -shl 8) -bor [int](($cc.B * $fsh) -shr 8)
    }
    for ($tu = 0; $tu -lt 16; $tu++) {
            $ua = $u0 + $du * $tu
            $ub = $ua + $du
            for ($tv = 0; $tv -lt 16; $tv++) {
                $va = $v0 + $dv * $tv
                $vb = $va + $dv
                # A model face is a 16x16 grid of texture cells. Split it into a
                # fixed clockwise fan, emitted per cell as u-fast / v-fast
                # triangles, so cell (tu,tv) never overlaps its neighbours.
                switch ($faceName) {
                    'up'    { $corn = @(
                            @(@($x0, $y1, $z0), $ua, $va), @(@($x1, $y1, $z0), $ub, $va),
                            @(@($x0, $y1, $z1), $ua, $vb), @(@($x1, $y1, $z1), $ub, $vb)) }
                    'east'  { $corn = @(
                            @(@($x1, $y1, $z1), $ua, $va), @(@($x1, $y1, $z0), $ub, $va),
                            @(@($x1, $y0, $z1), $ua, $vb), @(@($x1, $y0, $z0), $ub, $vb)) }
                    'south' { $corn = @(
                            @(@($x0, $y1, $z1), $ua, $va), @(@($x1, $y1, $z1), $ub, $va),
                            @(@($x0, $y0, $z1), $ua, $vb), @(@($x1, $y0, $z1), $ub, $vb)) }
                }
                $pxs = New-Object 'int[]' 4; $pys = New-Object 'int[]' 4
                for ($k = 0; $k -lt 4; $k++) {
                    $p = $corn[$k][0]
                    # 2:1 isometric projection onto the $us-unit block space
                    $c1 = ([double]$p[0] - [double]$p[2]) * 2.0 + $boxSize
                    $c2 = $boxSize - ([double]$p[1] * 2.0) + [double]$p[0] + [double]$p[2]
                    $pxs[$k] = [int][math]::Round($c1 * $ss / 4.0)
                    $pys[$k] = [int][math]::Round(($c2 * $ss / 8.0) + $yOff)
                }
                $sw = $tex.Width; $sh = $tex.Height
                if ($script:Diag -and $tu -eq 0 -and $tv -eq 0) {
                    for ($k = 0; $k -lt 4; $k++) {
                        Write-Host "DBG   $faceName corner$k uv=($($corn[$k][1]),$($corn[$k][2])) model=($($corn[$k][0] -join ',')) -> px=$($pxs[$k]) py=$($pys[$k])"
                    }
                    Write-Host "DBG   $faceName yOff=$yOff ss=$ss dim=$dim us=$us box y=$y0..$y1"
                }
                foreach ($tri in @(@(0, 1, 3), @(0, 3, 2))) {
                    $i0 = $tri[0]; $i1 = $tri[1]; $i2 = $tri[2]
                    $ax = $pxs[$i0]; $ay = $pys[$i0]; $bx = $pxs[$i1]; $by = $pys[$i1]; $ccx = $pxs[$i2]; $ccy = $pys[$i2]
                    # signed area: positive for a counter-clockwise triangle
                    $area = ($bx - $ax) * ($ccy - $ay) - ($by - $ay) * ($ccx - $ax)
                    if ([math]::Abs($area) -lt 1.0) { continue }
                    # Shrink the triangle towards its centroid before the inside test.
                    # Cells of one face tile exactly, so the test must accept every
                    # pixel centre this cell owns; the margin is in pixels.
                    $gx = ($ax + $bx + $ccx) / 3.0; $gy = ($ay + $by + $ccy) / 3.0
                    $vax = $ax; $vay = $ay; $vbx = $bx; $vby = $by; $vcx = $ccx; $vcy = $ccy
                    foreach ($vi in 1, 2, 3) {
                        if ($vi -eq 1) { $tx = $ax; $ty = $ay }
                        elseif ($vi -eq 2) { $tx = $bx; $ty = $by }
                        else { $tx = $ccx; $ty = $ccy }
                        $dx = $gx - $tx; $dy = $gy - $ty
                        $dl = [math]::Sqrt(($dx * $dx) + ($dy * $dy))
                        if ($dl -le 0) { continue }
                        $u = 1.0 / $dl
                        if ($u -gt 0.2) { $u = 0.2 }
                        $nx = $tx + ($dx * $u); $ny = $ty + ($dy * $u)
                        if ($vi -eq 1) { $vax = $nx; $vay = $ny }
                        elseif ($vi -eq 2) { $vbx = $nx; $vby = $ny }
                        else { $vcx = $nx; $vcy = $ny }
                    }
                    $loX = [int][math]::Floor([math]::Min($vax, [math]::Min($vbx, $vcx)))
                    $hiX = [int][math]::Ceiling([math]::Max($vax, [math]::Max($vbx, $vcx)))
                    $loY = [int][math]::Floor([math]::Min($vay, [math]::Min($vby, $vcy)))
                    $hiY = [int][math]::Ceiling([math]::Max($vay, [math]::Max($vby, $vcy)))
                    if ($loX -lt 0) { $loX = 0 }; if ($loY -lt 0) { $loY = 0 }
                    if ($hiX -gt ($dim - 1)) { $hiX = $dim - 1 }
                    if ($hiY -gt ($dim - 1)) { $hiY = $dim - 1 }
                    if ($hiX -lt $loX -or $hiY -lt $loY) { continue }
                    if ($script:Diag -and $tu -eq 0 -and $tv -eq 0) {
                        Write-Host "DBG   tri=$($tri -join ',') area=$area box=[$loX..$hiX,$loY..$hiY] a=($ax,$ay) b=($bx,$by) c=($ccx,$ccy)"
                    }
                    for ($py = $loY; $py -le $hiY; $py++) {
                        $wy = $py + 0.5
                        for ($px = $loX; $px -le $hiX; $px++) {
                            $wx = $px + 0.5
                            # edge functions, all with the sign of the triangle area
                            $t0 = (($vbx - $wx) * ($vcy - $wy)) - (($vby - $wy) * ($vcx - $wx))
                            $t1 = (($vcx - $wx) * ($vay - $wy)) - (($vcy - $wy) * ($vax - $wx))
                            $t2 = (($vax - $wx) * ($vby - $wy)) - (($vay - $wy) * ($vbx - $wx))
                            if ($area -gt 0) {
                                if ($t0 -lt 0 -or $t1 -lt 0 -or $t2 -lt 0) { continue }
                            } else {
                                if ($t0 -gt 0 -or $t1 -gt 0 -or $t2 -gt 0) { continue }
                            }
                            # the same edge functions on the unshrunk triangle are the
                            # barycentric weights, so the texture mapping is exact
                            $s0 = (($bx - $wx) * ($ccy - $wy)) - (($by - $wy) * ($ccx - $wx))
                            $s1 = (($ccx - $wx) * ($ay - $wy)) - (($ccy - $wy) * ($ax - $wx))
                            $s2 = (($ax - $wx) * ($by - $wy)) - (($ay - $wy) * ($bx - $wx))
                            $w0 = $s0 / $area; $w1 = $s1 / $area
                            $w2 = 1.0 - $w0 - $w1
                            $mtu = $corn[$i0][1] * $w0 + $corn[$i1][1] * $w1 + $corn[$i2][1] * $w2
                            $mtv = $corn[$i0][2] * $w0 + $corn[$i1][2] * $w1 + $corn[$i2][2] * $w2
                            $sx = [int][math]::Floor($mtu / $us * $sw); if ($sx -lt 0) { $sx = 0 }; if ($sx -ge $sw) { $sx = $sw - 1 }
                            $sy = [int][math]::Floor($mtv / $us * $sh); if ($sy -lt 0) { $sy = 0 }; if ($sy -ge $sh) { $sy = $sh - 1 }
                            $idx = $sy * $sw + $sx
                            $packed = $lut[$idx]
                            if ($packed -eq 0) { continue }
                            $buf[$py * $dim + $px] = $packed
                        }
                    }
                }
            }
        }
    }
}

function Write-RgbaPng([int[]] $buf, [int] $dim, [int] $ss, [int] $outSize, [string] $path) {
    if ($script:Diag) {
        $nz = 0
        $firstNz = -1
        for ($q = 0; $q -lt $buf.Length; $q++) { if ($buf[$q] -ne 0) { $nz++; if ($firstNz -lt 0) { $firstNz = $q } } }
        $probeIdx = 160 * $dim + 128
        if ($probeIdx -ge $buf.Length) { $probeIdx = $firstNz }
        Write-Host "DBG   Write-RgbaPng buf=$($buf.Length) nz=$nz firstNz=$firstNz buf[$probeIdx]=$($buf[$probeIdx]) dim=$dim ss=$ss outSize=$outSize step=$($ss * $ss)"
    }
    $final = New-Object System.Drawing.Bitmap($outSize, $outSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $written = 0
    try {
        for ($oy = 0; $oy -lt $outSize; $oy++) {
            for ($ox = 0; $ox -lt $outSize; $ox++) {
                $sumA = 0; $sumR = 0; $sumG = 0; $sumB = 0; $n = 0
                for ($sy = 0; $sy -lt $ss; $sy++) {
                    $row = ($oy * $ss + $sy) * $dim
                    for ($sx = 0; $sx -lt $ss; $sx++) {
                        $c = $buf[$row + ($ox * $ss + $sx)]
                        $a = ($c -shr 24) -band 0xFF
                        if ($a -eq 0) { continue }
                        $sumA += $a
                        # un-premultiply so the average keeps the true texel colour
                        $sumR += ((($c -shr 16) -band 0xFF) * $a)
                        $sumG += ((($c -shr 8) -band 0xFF) * $a)
                        $sumB += (($c -band 0xFF) * $a)
                        $n++
                    }
                }
                if ($n -eq 0 -or $sumA -eq 0) { continue }
                $written++
                $final.SetPixel($ox, $oy, [System.Drawing.Color]::FromArgb(
                    [int][math]::Round($sumA / $n),
                    [int][math]::Round($sumR / $sumA),
                    [int][math]::Round($sumG / $sumA),
                    [int][math]::Round($sumB / $sumA)))
            }
        }
    } finally { }
    if ($script:Diag) { Write-Host "DBG   Write-RgbaPng wrote $written/$($outSize * $outSize) pixels -> $path" }
    $final.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $final.Dispose()
}

# -------------------------------------------------------------------- run

$blocks = @()
foreach ($prop in (Get-ChildItem (Join-Path $generated "$modNs\blockstates") -Filter '*.json' | Sort-Object Name)) {
    $name = $prop.BaseName
    if ($name -match '_icon$') { continue }
    if ($Filter.Count -gt 0 -and -not ($Filter | Where-Object { $name -like "*$_*" })) { continue }
    $modelIds = Get-BlockModelIds "$modNs`:$name"
    if ($modelIds.Count -eq 0) { Write-Warning "no model for $name"; continue }
    $blocks += [pscustomobject]@{ name = $name; models = @($modelIds | Select-Object -Unique) }
}
Write-Host "blocks: $($blocks.Count)"

$dim = $Size * $SuperSample
$shift = [int][math]::Log($SuperSample, 2)

if ($NoRun) { return }

if ($ListOnly) { $blocks | ForEach-Object { $_.name + ' => ' + ($_.models -join ', ') } ; exit 0 }

Write-Host "canvas: $dim x $dim (supersample $SuperSample) -> $Size x $Size"

$ok = 0; $failed = @()
foreach ($b in $blocks) {
    $resolved = @()
    foreach ($m in $b.models) {
        $r = Resolve-ModelSafe $m
        if ($r -and $r.elements.Count -gt 0) { $resolved += $r }
    }
    if ($resolved.Count -eq 0) { $failed += $b.name; continue }

    $buf = New-Rgba $dim $dim
    # Every model is normalised into the same block space first: models authored
    # on a 16-unit grid and models authored on a 128-unit grid must produce the
    # same icon, so the scale is derived from the model's own bounding box.
    $us = 16.0
    $mn = @(1e9, 1e9, 1e9)
    $mx = @(-1e9, -1e9, -1e9)
    foreach ($r in $resolved) {
        foreach ($el in $r.elements) {
            foreach ($i in 0, 1, 2) {
                $a = [double]$el.from[$i]; $b2 = [double]$el.to[$i]
                if ($a -gt $b2) { $t = $a; $a = $b2; $b2 = $t }
                if ($a -lt $mn[$i]) { $mn[$i] = $a }
                if ($b2 -gt $mx[$i]) { $mx[$i] = $b2 }
            }
        }
    }
    $ext = [math]::Max(($mx[0] - $mn[0]), [math]::Max(($mx[1] - $mn[1]), ($mx[2] - $mn[2])))
    if ($ext -le 0) { $failed += $b.name; continue }
    $mscale = $us / $ext
    # Centre the box on the block-space origin so a cube lands on 0..16 and a
    # slab or an eighth is centred inside that footprint.
    $org = @(0.0, 0.0, 0.0)
    for ($i = 0; $i -lt 3; $i++) {
        $org[$i] = -1.0 * ($mn[$i] + $mx[$i]) * $mscale / 2.0
    }
    # painter order: elements further from the camera first
    $order = @()
    foreach ($r in $resolved) {
        foreach ($el in $r.elements) {
            $d = [double]$el.from[0] + [double]$el.from[1] + [double]$el.from[2]
            $order += [pscustomobject]@{ d = $d; el = $el; textures = $r.textures }
        }
    }
    foreach ($o in ($order | Sort-Object d)) {
        Draw-Element $o.el $o.textures $buf $dim $SuperSample $mscale $org $us
    }
    if ($script:Diag) {
        $nz = 0
        for ($q = 0; $q -lt $buf.Length; $q++) { if ($buf[$q] -ne 0) { $nz++ } }
        Write-Host "DBG   $($b.name): buffer fill $nz / $($buf.Length) us=$us ext=$ext mscale=$mscale org=$($org -join ',')"
    }
    $outPath = Join-Path $outDir "$($b.name).png"
    Write-RgbaPng $buf $dim $SuperSample $Size $outPath
    $ok++
}

$jar.Dispose()
Write-Host "wrote $ok icons to $outDir"
if ($failed.Count -gt 0) { Write-Host "no elements for: $($failed -join ', ')" }
