package io.github.jvmdc.dungeongen.corridor;

/** Union-find. find walks the parent chain in a loop so a long chain cannot overflow the stack. */
final class DisjointSet {
    private final int[] parent;

    DisjointSet(int size) {
        parent = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }
    }

    int find(int v) {
        int root = v;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[v] != root) {
            int next = parent[v];
            parent[v] = root;
            v = next;
        }
        return root;
    }

    /** Link rootB under rootA. True when the two rooms were in different sets. */
    boolean unite(int a, int b) {
        a = find(a);
        b = find(b);
        if (a == b) {
            return false;
        }
        parent[b] = a;
        return true;
    }

    int components() {
        int count = 0;
        for (int i = 0; i < parent.length; i++) {
            if (find(i) == i) {
                count++;
            }
        }
        return count;
    }
}
