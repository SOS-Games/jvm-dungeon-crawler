"""Generate AnimalAttrs.java from Veloren SkeletonAttr From impls. Run once."""
import re
import pathlib

root = pathlib.Path(r"D:\coding\veloren\voxygen\anim\src")
out = pathlib.Path(
    r"D:\coding\jvm-dungeon-crawler\core\src\main\java\io\github\jvmdc\viewer\AnimalAttrs.java"
)
bodies = [
    "quadruped_small",
    "quadruped_medium",
    "quadruped_low",
    "theropod",
    "bird_large",
    "fish_small",
    "fish_medium",
    "dragon",
    "arthropod",
    "crustacean",
]
prefix = {
    "quadruped_small": "QS",
    "quadruped_medium": "QM",
    "quadruped_low": "QL",
    "theropod": "TH",
    "bird_large": "BL",
    "fish_small": "FS",
    "fish_medium": "FM",
    "dragon": "DR",
    "arthropod": "AR",
    "crustacean": "CR",
}


def brace_body(text, start):
    i = text.find("{", start)
    if i < 0:
        raise SystemExit("no brace near " + text[max(0, start) : start + 80])
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[i + 1 : j]
    raise SystemExit("unbalanced")


def parse_value(s):
    s = s.strip().rstrip(",")
    if s == "true":
        return [1.0]
    if s == "false":
        return [0.0]
    nums = re.findall(r"-?\d+(?:\.\d+)?", s)
    if not nums:
        return None
    return [float(x) for x in nums]


def pattern_kind(groups):
    """Every (Species, Sex) group before => is one arm, including | chains."""
    if not groups:
        return None
    if len(groups) == 1 and groups[0].strip() == "_":
        return ("wild",)
    species = []
    sexes = set()
    for group in groups:
        group = group.strip()
        if group == "_" or "," not in group:
            continue
        names, sex = group.rsplit(",", 1)
        sexes.add(sex.strip())
        for name in names.split("|"):
            name = name.strip()
            if name and name != "_":
                species.append(name)
    if not species:
        return ("wild",)
    if len(sexes) != 1:
        raise SystemExit("mixed sexes in " + str(groups))
    return ("arm", species, next(iter(sexes)))


def arms_of(match_body):
    lines = []
    for line in match_body.splitlines():
        if "//" in line:
            line = line[: line.index("//")]
        lines.append(line)
    text = "\n".join(lines)
    arms = []
    i = 0
    while True:
        k = text.find("=>", i)
        if k < 0:
            break
        pre = text[i:k]
        depth = 0
        start = None
        groups = []
        for idx, ch in enumerate(pre):
            if ch == "(":
                if depth == 0:
                    start = idx
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0 and start is not None:
                    groups.append(pre[start + 1 : idx])
                    start = None
        if not groups:
            i = k + 2
            continue
        pat = groups[-1].strip()
        j = k + 2
        depth = 0
        val = ""
        while j < len(text):
            ch = text[j]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            elif ch == "," and depth == 0:
                break
            val += ch
            j += 1
        value = parse_value(val)
        kind = pattern_kind(groups)
        if kind and value is not None:
            arms.append((kind, value))
        else:
            raise SystemExit("bad arm " + pat + " => " + val[:80])
        i = j + 1
    return arms


def fields_of(body):
    text = (root / body / "mod.rs").read_text(encoding="utf-8")
    fn = brace_body(text, text.find("fn from(body"))
    selfb = brace_body(fn, re.search(r"Self\s*\{", fn).start())
    fields = []
    depth = 0
    cur = ""
    for ch in selfb:
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
        if ch == "," and depth == 0:
            if cur.strip():
                fields.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        fields.append(cur.strip())
    layout = []
    cursor = 0
    for field in fields:
        name, _, rest = field.partition(":")
        name = name.strip().split()[-1]
        if "match (" not in rest:
            continue
        mb = brace_body(rest, rest.find("match"))
        arms = arms_of(mb)
        width = max(len(v) for _, v in arms)
        assigned = {}
        default = None
        species_here = set()
        for kind, value in arms:
            value = value + [0.0] * (width - len(value))
            if kind[0] == "wild":
                default = value
                continue
            _, species, sex = kind
            sexes = ["male", "female"] if sex == "_" else [sex.lower()]
            for sp in species:
                key = sp.lower()
                species_here.add(key)
                for sx in sexes:
                    assigned.setdefault((key, sx), value)
        if default is not None:
            for key in species_here:
                for sx in ("male", "female"):
                    assigned.setdefault((key, sx), default)
        layout.append((name, cursor, width, assigned, default))
        cursor += width
    return layout, cursor


def fmt(v):
    if abs(v - round(v)) < 1e-6:
        return f"{int(round(v))}f"
    return f"{v:.4f}f"


def main():
    lines = [
        "/*",
        " * Generated from Veloren voxygen/anim SkeletonAttr From impls.",
        " * SPDX-License-Identifier: GPL-3.0-or-later",
        " */",
        "package io.github.jvmdc.viewer;",
        "",
        "import java.util.HashMap;",
        "import java.util.Map;",
        "",
        "/** Rest-pose numbers for each animal species. Idle motion reads these. */",
        "final class AnimalAttrs {",
        "    private AnimalAttrs() {}",
        "",
    ]
    maps = {}
    for body in bodies:
        layout, total = fields_of(body)
        all_species = set()
        for _name, _off, _width, assigned, _default in layout:
            for key, _sx in assigned:
                all_species.add(key)
        table = {}
        for sp in sorted(all_species):
            male = []
            female = []
            for _name, _off, width, assigned, default in layout:
                z = default if default is not None else [0.0] * width
                male.extend(assigned.get((sp, "male"), z))
                female.extend(assigned.get((sp, "female"), z))
            if male == female:
                table[sp] = male
            else:
                table[sp + "/male"] = male
                table[sp + "/female"] = female
        maps[body] = table
        p = prefix[body]
        lines.append("    // " + body)
        for name, off, _width, _assigned, _default in layout:
            lines.append(f"    static final int {p}_{name.upper()} = {off};")
        lines.append(f"    static final int {p}_LEN = {total};")
        lines.append("")
        print(body, "species", len(table), "floats", total)

    lines.append("    private static final Map<String, float[]> TABLE = new HashMap<>();")
    lines.append("    static {")
    for body, table in maps.items():
        for key, vals in sorted(table.items()):
            nums = ", ".join(fmt(v) for v in vals)
            lines.append(f'        put("{body}/{key}", new float[] {{{nums}}});')
    lines.append("    }")
    lines.append("")
    lines.append("    private static void put(String key, float[] value) { TABLE.put(key, value); }")
    lines.append("")
    lines.append('    /** speciesSex is "pig/male". Falls back to the species, then the first row. */')
    lines.append("    static float[] get(String family, String speciesSex) {")
    lines.append('        float[] row = TABLE.get(family + "/" + speciesSex);')
    lines.append("        if (row == null && speciesSex != null) {")
    lines.append("            int slash = speciesSex.indexOf('/');")
    lines.append('            if (slash > 0) row = TABLE.get(family + "/" + speciesSex.substring(0, slash));')
    lines.append("        }")
    lines.append("        if (row == null) {")
    lines.append("            for (var e : TABLE.entrySet()) {")
    lines.append('                if (e.getKey().startsWith(family + "/")) return e.getValue();')
    lines.append("            }")
    lines.append("            return new float[0];")
    lines.append("        }")
    lines.append("        return row;")
    lines.append("    }")
    lines.append("}")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    pig = maps["quadruped_small"]["pig"]
    print("pig head/chest", pig[:4], "len", len(pig))
    print("wrote", out, "lines", len(lines))


if __name__ == "__main__":
    main()
