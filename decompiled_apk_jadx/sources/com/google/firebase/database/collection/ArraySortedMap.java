package com.google.firebase.database.collection;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.LLRBNode;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/* loaded from: classes.dex */
public class ArraySortedMap<K, V> extends ImmutableSortedMap<K, V> {
    private final Comparator<K> comparator;
    private final K[] keys;
    private final V[] values;

    public static <A, B, C> ArraySortedMap<A, C> buildFrom(List<A> keys, Map<B, C> values, ImmutableSortedMap.Builder.KeyTranslator<A, B> translator, Comparator<A> comparator) {
        Collections.sort(keys, comparator);
        int size = keys.size();
        Object[] objArr = new Object[size];
        Object[] objArr2 = new Object[size];
        int pos = 0;
        for (A k : keys) {
            objArr[pos] = k;
            C value = values.get(translator.translate(k));
            objArr2[pos] = value;
            pos++;
        }
        return new ArraySortedMap<>(comparator, objArr, objArr2);
    }

    public static <K, V> ArraySortedMap<K, V> fromMap(Map<K, V> map, Comparator<K> comparator) {
        return buildFrom(new ArrayList(map.keySet()), map, ImmutableSortedMap.Builder.identityTranslator(), comparator);
    }

    public ArraySortedMap(Comparator<K> comparator) {
        this.keys = (K[]) new Object[0];
        this.values = (V[]) new Object[0];
        this.comparator = comparator;
    }

    private ArraySortedMap(Comparator<K> comparator, K[] keys, V[] values) {
        this.keys = keys;
        this.values = values;
        this.comparator = comparator;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public boolean containsKey(K key) {
        return findKey(key) != -1;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public V get(K key) {
        int pos = findKey(key);
        if (pos != -1) {
            return this.values[pos];
        }
        return null;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public ImmutableSortedMap<K, V> remove(K key) {
        int pos = findKey(key);
        if (pos == -1) {
            return this;
        }
        return new ArraySortedMap(this.comparator, removeFromArray(this.keys, pos), removeFromArray(this.values, pos));
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public ImmutableSortedMap<K, V> insert(K key, V value) {
        int pos = findKey(key);
        if (pos != -1) {
            if (this.keys[pos] == key && this.values[pos] == value) {
                return this;
            }
            return new ArraySortedMap(this.comparator, replaceInArray(this.keys, pos, key), replaceInArray(this.values, pos, value));
        }
        K[] newKeys = this.keys;
        if (newKeys.length > 25) {
            Map<K, V> map = new HashMap<>(this.keys.length + 1);
            for (int i = 0; i < this.keys.length; i++) {
                map.put(this.keys[i], this.values[i]);
            }
            map.put(key, value);
            return RBTreeSortedMap.fromMap(map, this.comparator);
        }
        int newPos = findKeyOrInsertPosition(key);
        return new ArraySortedMap(this.comparator, addToArray(this.keys, newPos, key), addToArray(this.values, newPos, value));
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public K getMinKey() {
        if (this.keys.length > 0) {
            return this.keys[0];
        }
        return null;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public K getMaxKey() {
        if (this.keys.length > 0) {
            return this.keys[this.keys.length - 1];
        }
        return null;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public int size() {
        return this.keys.length;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public boolean isEmpty() {
        return this.keys.length == 0;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public void inOrderTraversal(LLRBNode.NodeVisitor<K, V> visitor) {
        for (int i = 0; i < this.keys.length; i++) {
            visitor.visitEntry(this.keys[i], this.values[i]);
        }
    }

    private Iterator<Map.Entry<K, V>> iterator(final int pos, final boolean reverse) {
        return new Iterator<Map.Entry<K, V>>() { // from class: com.google.firebase.database.collection.ArraySortedMap.1
            int currentPos;

            {
                this.currentPos = pos;
            }

            @Override // java.util.Iterator
            public boolean hasNext() {
                if (reverse) {
                    if (this.currentPos >= 0) {
                        return true;
                    }
                } else if (this.currentPos < ArraySortedMap.this.keys.length) {
                    return true;
                }
                return false;
            }

            @Override // java.util.Iterator
            public Map.Entry<K, V> next() {
                Object obj = ArraySortedMap.this.keys[this.currentPos];
                Object obj2 = ArraySortedMap.this.values[this.currentPos];
                this.currentPos = reverse ? this.currentPos - 1 : this.currentPos + 1;
                return new AbstractMap.SimpleImmutableEntry(obj, obj2);
            }

            @Override // java.util.Iterator
            public void remove() {
                throw new UnsupportedOperationException("Can't remove elements from ImmutableSortedMap");
            }
        };
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap, java.lang.Iterable
    public Iterator<Map.Entry<K, V>> iterator() {
        return iterator(0, false);
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public Iterator<Map.Entry<K, V>> iteratorFrom(K key) {
        int pos = findKeyOrInsertPosition(key);
        return iterator(pos, false);
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public Iterator<Map.Entry<K, V>> reverseIteratorFrom(K key) {
        int pos = findKeyOrInsertPosition(key);
        if (pos < this.keys.length && this.comparator.compare(this.keys[pos], key) == 0) {
            return iterator(pos, true);
        }
        return iterator(pos - 1, true);
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public Iterator<Map.Entry<K, V>> reverseIterator() {
        return iterator(this.keys.length - 1, true);
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public K getPredecessorKey(K key) {
        int pos = findKey(key);
        if (pos == -1) {
            throw new IllegalArgumentException("Can't find predecessor of nonexistent key");
        }
        if (pos > 0) {
            return this.keys[pos - 1];
        }
        return null;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public K getSuccessorKey(K key) {
        int pos = findKey(key);
        if (pos == -1) {
            throw new IllegalArgumentException("Can't find successor of nonexistent key");
        }
        if (pos < this.keys.length - 1) {
            return this.keys[pos + 1];
        }
        return null;
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public int indexOf(K key) {
        return findKey(key);
    }

    @Override // com.google.firebase.database.collection.ImmutableSortedMap
    public Comparator<K> getComparator() {
        return this.comparator;
    }

    private static <T> T[] removeFromArray(T[] arr, int pos) {
        int newSize = arr.length - 1;
        T[] newArray = (T[]) new Object[newSize];
        System.arraycopy(arr, 0, newArray, 0, pos);
        System.arraycopy(arr, pos + 1, newArray, pos, newSize - pos);
        return newArray;
    }

    private static <T> T[] addToArray(T[] arr, int pos, T value) {
        int newSize = arr.length + 1;
        T[] newArray = (T[]) new Object[newSize];
        System.arraycopy(arr, 0, newArray, 0, pos);
        newArray[pos] = value;
        System.arraycopy(arr, pos, newArray, pos + 1, (newSize - pos) - 1);
        return newArray;
    }

    private static <T> T[] replaceInArray(T[] arr, int pos, T value) {
        int size = arr.length;
        T[] newArray = (T[]) new Object[size];
        System.arraycopy(arr, 0, newArray, 0, size);
        newArray[pos] = value;
        return newArray;
    }

    private int findKeyOrInsertPosition(K key) {
        int newPos = 0;
        while (newPos < this.keys.length && this.comparator.compare(this.keys[newPos], key) < 0) {
            newPos++;
        }
        return newPos;
    }

    private int findKey(K key) {
        K[] kArr;
        int i = 0;
        for (K otherKey : this.keys) {
            if (this.comparator.compare(key, otherKey) != 0) {
                i++;
            } else {
                return i;
            }
        }
        return -1;
    }
}
