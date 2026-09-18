// Vendored third-party library: PinIn by Towdium (https://github.com/Towdium/PinIn), MIT License.
// Package renamed to com.plumejade.lensouls.reinforce.pinyin and kept self-contained (only java.* and fastutil from Minecraft).
// Originally vendored by JustEnoughCharacters / Remorphed as plume.summoner.pinin; see docs/ for details.
package com.plumejade.lensouls.reinforce.pinyin.utils;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Author: Towdium
 * Date: 04/03/19
 */
public class Cache<K, V> {
    HashMap<K, V> data = new HashMap<>();
    Function<K, V> generator;

    public Cache(Function<K, V> generator) {
        this.generator = generator;
    }

    public V get(K key) {
        V ret = data.get(key);
        if (ret == null) {
            ret = generator.apply(key);
            if (ret != null) data.put(key, ret);
        }
        return ret;
    }

    public void foreach(BiConsumer<K, V> c) {
        data.forEach(c);
    }

    public void clear() {
        data.clear();
    }
}
