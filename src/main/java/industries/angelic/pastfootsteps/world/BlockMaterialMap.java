package industries.angelic.pastfootsteps.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.JsonParseException;

/** Compiled block material selectors. Instances are immutable apart from their bounded lookup cache. */
final class BlockMaterialMap {

    interface Groups {

        String[] names();

        List<ItemStack> entries(String name);
    }

    static final Groups ORE_DICTIONARY = new Groups() {

        @Override
        public String[] names() {
            return OreDictionary.getOreNames();
        }

        @Override
        public List<ItemStack> entries(String name) {
            return OreDictionary.getOres(name, false);
        }
    };

    private static final int MAX_KEY_LENGTH = 256;
    private static final int MAX_CACHE_ENTRIES = 2048;
    private static final String[] SUBSTRATES = { "carpet", "foliage", "foliage_low", "messy", "wet", "bigger" };

    private final Map<String, String> exact;
    private final List<GroupRule> groups;
    private final List<GlobRule> globs;
    private final Map<String, String> cache = new LinkedHashMap<String, String>(256, 0.75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    BlockMaterialMap(Map<String, String> merged, Groups oreGroups) {
        Map<String, String> exactRules = new LinkedHashMap<String, String>();
        List<GroupRule> groupRules = new ArrayList<GroupRule>();
        List<GlobRule> globRules = new ArrayList<GlobRule>();
        if (merged != null) {
            int declaration = 0;
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                String key = entry.getKey() == null ? ""
                    : entry.getKey()
                        .toLowerCase(Locale.ROOT);
                if (!active(key)) {
                    if (!key.startsWith("#")) exactRules.put(key, entry.getValue());
                    continue;
                }
                validateKey(key);
                Parsed parsed = parse(key);
                Rule rule = new Rule(parsed.meta, parsed.substrate, entry.getValue(), declaration++);
                if (parsed.kind == Kind.GROUP) groupRules.add(new GroupRule(parsed.selector, rule));
                else globRules.add(new GlobRule(parsed.selector, rule));
            }
        }
        exact = Collections.unmodifiableMap(exactRules);
        groups = Collections.unmodifiableList(compileGroups(groupRules, oreGroups));
        globs = Collections.unmodifiableList(globRules);
    }

    String lookup(String normalizedRegistryName, int meta, String normalizedSubstrate) {
        if (normalizedRegistryName == null || normalizedSubstrate == null) return null;
        meta &= 15;
        String name = normalizedRegistryName.toLowerCase(Locale.ROOT);
        String substrate = normalizedSubstrate.toLowerCase(Locale.ROOT);
        String cacheKey = name + "\u0000" + meta + "\u0000" + substrate;
        synchronized (cache) {
            if (cache.containsKey(cacheKey)) return cache.get(cacheKey);
        }
        String result = exactLookup(name, meta, substrate);
        if (result == null) result = broaderLookup(groups, name, meta, substrate);
        if (result == null) result = broaderLookup(globs, name, meta, substrate);
        synchronized (cache) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    private static boolean active(String key) {
        return key.startsWith("#ore:") || !key.startsWith("#") && key.indexOf('*') >= 0;
    }

    static void validateKey(String normalizedKey) throws JsonParseException {
        if (normalizedKey == null || !active(normalizedKey)) return;
        if (normalizedKey.length() > MAX_KEY_LENGTH) throw invalid(normalizedKey);
        parse(normalizedKey);
    }

    private static Parsed parse(String key) {
        if (key.startsWith("#ore:")) {
            String body = key.substring(5);
            ParsedSuffix suffix = suffix(body);
            String group = suffix.body;
            int meta = metadata(group, key);
            group = stripMetadata(group);
            if (group.isEmpty() || !group.matches("[a-z0-9_.:-]+")) throw invalid(key);
            return new Parsed(Kind.GROUP, group, meta, suffix.substrate);
        }
        if (key.indexOf('*') >= 0) {
            if (key.indexOf('?') >= 0 || key.indexOf('[') >= 0 || key.indexOf(']') >= 0) throw invalid(key);
            ParsedSuffix suffix = suffix(key);
            int meta = metadata(suffix.body, key);
            String body = stripMetadata(suffix.body);
            if (body.isEmpty() || !validGlob(body)) throw invalid(key);
            return new Parsed(Kind.GLOB, body, meta, suffix.substrate);
        }
        throw invalid(key);
    }

    private static List<GroupRule> compileGroups(List<GroupRule> source, Groups oreGroups) {
        if (source.isEmpty()) return new ArrayList<GroupRule>();
        Set<String> referenced = new LinkedHashSet<String>();
        for (GroupRule rule : source) referenced.add(rule.group);
        Map<String, Set<String>> memberships = new LinkedHashMap<String, Set<String>>();
        for (String group : referenced) memberships.put(group, new LinkedHashSet<String>());
        for (String registeredGroup : oreGroups.names()) {
            Set<String> entries = memberships.get(registeredGroup.toLowerCase(Locale.ROOT));
            if (entries == null) continue;
            for (ItemStack stack : oreGroups.entries(registeredGroup)) {
                if (stack == null || !(stack.getItem() instanceof ItemBlock)) continue;
                Block block = ((ItemBlock) stack.getItem()).field_150939_a;
                Object registered = Block.blockRegistry.getNameForObject(block);
                if (!(registered instanceof String)) continue;
                String name = ((String) registered).toLowerCase(Locale.ROOT);
                int damage = stack.getItemDamage();
                if (damage == OreDictionary.WILDCARD_VALUE) entries.add(name + "#*");
                else if (damage >= 0 && damage <= 15) entries.add(name + "#" + damage);
            }
        }
        List<GroupRule> result = new ArrayList<GroupRule>(source.size());
        for (GroupRule rule : source) result.add(rule.withMembership(memberships.get(rule.group)));
        return result;
    }

    private String exactLookup(String name, int meta, String substrate) {
        String result = exact.get(key(name, meta, substrate));
        if (result != null) return result;
        result = exact.get(key(name, -1, substrate));
        if (result != null) return result;
        if (name.startsWith("minecraft:")) {
            String bare = name.substring(10);
            result = exact.get(key(bare, meta, substrate));
            if (result == null) result = exact.get(key(bare, -1, substrate));
        }
        return result;
    }

    private static String key(String name, int meta, String substrate) {
        return name + (meta < 0 ? "" : "^" + meta) + (substrate.isEmpty() ? "" : "." + substrate);
    }

    private static String broaderLookup(List<? extends SelectorRule> rules, String name, int meta, String substrate) {
        Rule best = null;
        int bestSpecificity = Integer.MIN_VALUE;
        for (SelectorRule selector : rules) {
            if (!selector.matches(name, meta, substrate)) continue;
            Rule candidate = selector.rule;
            int candidateSpecificity = selector.specificity();
            if (best == null || candidateSpecificity > bestSpecificity
                || candidateSpecificity == bestSpecificity && candidate.declaration > best.declaration) {
                best = candidate;
                bestSpecificity = candidateSpecificity;
            }
        }
        return best == null ? null : best.value;
    }

    private static ParsedSuffix suffix(String key) {
        for (String suffix : SUBSTRATES) {
            String ending = "." + suffix;
            if (key.endsWith(ending)) return new ParsedSuffix(key.substring(0, key.length() - ending.length()), suffix);
        }
        return new ParsedSuffix(key, "");
    }

    private static int metadata(String key, String original) {
        int marker = key.lastIndexOf('^');
        if (marker < 0) return -1;
        if (marker == key.length() - 1 || marker != key.indexOf('^')) throw invalid(original);
        String value = key.substring(marker + 1);
        if (!value.matches("[0-9]+")) throw invalid(original);
        try {
            int result = Integer.parseInt(value);
            if (result < 0 || result > 15) throw invalid(original);
            return result;
        } catch (NumberFormatException error) {
            throw invalid(original);
        }
    }

    private static String stripMetadata(String key) {
        int marker = key.lastIndexOf('^');
        return marker < 0 ? key : key.substring(0, marker);
    }

    private static boolean validGlob(String value) {
        return value.matches("[a-z0-9_.*-]+(?::[a-z0-9_.*-]+)?");
    }

    private static JsonParseException invalid(String key) {
        return new JsonParseException("invalid active material map key '" + key + "'");
    }

    private enum Kind {
        GROUP,
        GLOB
    }

    private static final class Parsed {

        final Kind kind;
        final String selector, substrate;
        final int meta;

        Parsed(Kind kind, String selector, int meta, String substrate) {
            this.kind = kind;
            this.selector = selector;
            this.meta = meta;
            this.substrate = substrate;
        }
    }

    private static final class ParsedSuffix {

        final String body, substrate;

        ParsedSuffix(String body, String substrate) {
            this.body = body;
            this.substrate = substrate;
        }
    }

    private static final class Rule {

        final int meta, declaration;
        final String substrate, value;

        Rule(int meta, String substrate, String value, int declaration) {
            this.meta = meta;
            this.substrate = substrate;
            this.value = value;
            this.declaration = declaration;
        }

        int specificity() {
            return (meta < 0 ? 0 : 100000) + (substrate.isEmpty() ? 0 : 1000);
        }
    }

    private abstract static class SelectorRule {

        final Rule rule;

        SelectorRule(Rule rule) {
            this.rule = rule;
        }

        abstract boolean matches(String name, int meta, String substrate);

        int specificity() {
            return rule.specificity();
        }
    }

    private static final class GroupRule extends SelectorRule {

        final String group;
        final Set<String> members;

        GroupRule(String group, Rule rule) {
            this(group, rule, Collections.<String>emptySet());
        }

        GroupRule(String group, Rule rule, Set<String> members) {
            super(rule);
            this.group = group;
            this.members = members == null ? Collections.<String>emptySet() : members;
        }

        GroupRule withMembership(Set<String> values) {
            return new GroupRule(group, rule, values);
        }

        @Override
        boolean matches(String name, int meta, String substrate) {
            return rule.substrate.equals(substrate) && (rule.meta < 0 || rule.meta == meta)
                && (members.contains(name + "#" + meta) || members.contains(name + "#*"));
        }

        @Override
        int specificity() {
            return rule.specificity();
        }
    }

    private static final class GlobRule extends SelectorRule {

        final String pattern;

        GlobRule(String pattern, Rule rule) {
            super(rule);
            this.pattern = pattern.indexOf(':') < 0 && !pattern.equals("*") ? "minecraft:" + pattern : pattern;
        }

        @Override
        boolean matches(String name, int meta, String substrate) {
            return rule.substrate.equals(substrate) && (rule.meta < 0 || rule.meta == meta) && match(pattern, name);
        }

        @Override
        int specificity() {
            return rule.specificity() + pattern.replace("*", "")
                .length();
        }

        private static boolean match(String pattern, String value) {
            int p = 0, v = 0, star = -1, retry = 0;
            while (v < value.length()) {
                if (p < pattern.length() && pattern.charAt(p) == '*') {
                    star = p++;
                    retry = v;
                } else if (p < pattern.length() && pattern.charAt(p) == value.charAt(v)) {
                    p++;
                    v++;
                } else if (star >= 0) {
                    p = star + 1;
                    v = ++retry;
                } else return false;
            }
            while (p < pattern.length() && pattern.charAt(p) == '*') p++;
            return p == pattern.length();
        }
    }
}
