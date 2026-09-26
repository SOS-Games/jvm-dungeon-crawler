package io.github.jvmdc.converter;

import org.antlr.v4.runtime.tree.TerminalNode;

public class RonToJsonVisitor extends RonBaseVisitor<String> {

    @Override
    public String visitRon_(RonParser.Ron_Context ctx) {
        if (ctx.value() == null || ctx.value().isEmpty()) {
            return "{}";
        }
        if (ctx.value().size() == 1) {
            return visit(ctx.value(0));
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ctx.value().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(visit(ctx.value(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String visitValue(RonParser.ValueContext ctx) {
        // 1. Determine inner JSON representation
        String innerJson;
        if (ctx.struct() != null) {
            innerJson = visit(ctx.struct());
        } else if (ctx.tuple() != null) {
            innerJson = visit(ctx.tuple());
        } else if (ctx.list() != null) {
            innerJson = visit(ctx.list());
        } else if (ctx.map() != null) {
            innerJson = visit(ctx.map());
        } else {
            int childIdx = ctx.classname() != null ? 1 : 0;
            innerJson = visit(ctx.getChild(childIdx));
        }

        // 2. Handle Rust named structs & enum variants
        if (ctx.classname() != null) {
            String typeName = ctx.classname().getText();

            // Unwrap Some("foo") -> "foo"
            if ("Some".equals(typeName)) {
                if (ctx.tuple() != null && ctx.tuple().values() != null && !ctx.tuple().values().value().isEmpty()) {
                    return visit(ctx.tuple().values().value(0));
                }
                return innerJson;
            }

            // Unwrap root containers like ItemDef(...)
            if ("ItemDef".equals(typeName)) {
                return innerJson;
            }

            // Single-element tuple struct like Armor((...)) or FromSet("Adventurer") -> unwrap the single child
            if (ctx.tuple() != null && ctx.tuple().values() != null && ctx.tuple().values().value().size() == 1) {
                String singleValue = visit(ctx.tuple().values().value(0));
                return "{\"" + typeName + "\": " + singleValue + "}";
            }

            // Multi-element tuple struct like SpriteWithCfg(A, B) or Filled(A, B) -> keep as array: {"Filled": [A, B]}
            return "{\"" + typeName + "\": " + innerJson + "}";
        }

        return innerJson;
    }

    @Override
    public String visitStruct(RonParser.StructContext ctx) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ctx.structitem().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(visit(ctx.structitem(i)));
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String visitStructitem(RonParser.StructitemContext ctx) {
        String key = ctx.getChild(0).getText();
        if (key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1);
        }
        String value = visit(ctx.value());
        return "\"" + key + "\": " + value;
    }

    @Override
    public String visitTuple(RonParser.TupleContext ctx) {
        if (ctx.values() == null) {
            return "[]";
        }
        return "[" + visit(ctx.values()) + "]";
    }

    @Override
    public String visitList(RonParser.ListContext ctx) {
        if (ctx.values() == null) {
            return "[]";
        }
        return "[" + visit(ctx.values()) + "]";
    }

    @Override
    public String visitValues(RonParser.ValuesContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.value().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(visit(ctx.value(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitMap(RonParser.MapContext ctx) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ctx.mapitem().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(visit(ctx.mapitem(i)));
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String visitMapitem(RonParser.MapitemContext ctx) {
        // ctx.value(0) is the key, ctx.value(1) is the value
        String rawKey = ctx.value(0).getText();
        
        // Ensure the key is wrapped in quotes and interior quotes are escaped
        String key;
        if (rawKey.startsWith("\"") && rawKey.endsWith("\"")) {
            key = rawKey;
        } else {
            key = "\"" + rawKey.replace("\"", "\\\"") + "\"";
        }
    
        String val = visit(ctx.value(1));
        return key + ": " + val;
    }

    @Override
    public String visitTerminal(TerminalNode node) {
        String text = node.getText();

        if ("None".equals(text)) {
            return "null";
        }
        if ("true".equals(text) || "false".equals(text) || text.startsWith("\"") || isNumber(text)) {
            return text;
        }
        // Unquoted Rust enum/symbol (e.g. Back, Low, Sword) -> wrap in quotes for valid JSON
        if (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_') {
            return "\"" + text + "\"";
        }

        return text;
    }

    private boolean isNumber(String s) {
        return s.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
    }

    @Override
    protected String defaultResult() {
        return "";
    }

    @Override
    protected String aggregateResult(String aggregate, String nextResult) {
        if (aggregate == null || aggregate.isEmpty()) return nextResult;
        if (nextResult == null || nextResult.isEmpty()) return aggregate;
        return aggregate + nextResult;
    }
}