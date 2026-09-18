// Vendored third-party library: PinIn by Towdium (https://github.com/Towdium/PinIn), MIT License.
// Package renamed to com.plumejade.lensouls.reinforce.pinyin and kept self-contained (only java.* and fastutil from Minecraft).
// Originally vendored by JustEnoughCharacters / Remorphed as plume.summoner.pinin; see docs/ for details.
package com.plumejade.lensouls.reinforce.pinyin.searchers;

import com.plumejade.lensouls.reinforce.pinyin.PinIn;
import com.plumejade.lensouls.reinforce.pinyin.utils.Accelerator;

import java.util.List;

public interface Searcher<T> {
    void put(String name, T identifier);

    List<T> search(String name);

    PinIn context();

    enum Logic {
        BEGIN {
            @Override
            public boolean test(Accelerator a, int offset, int start) {
                return a.begins(offset, start);
            }

            @Override
            public boolean test(PinIn p, String s1, String s2) {
                return p.begins(s1, s2);
            }

            @Override
            public boolean raw(String s1, String s2) {
                return s1.startsWith(s2);
            }
        },

        CONTAIN {
            @Override
            public boolean test(Accelerator a, int offset, int start) {
                return a.contains(offset, start);
            }

            @Override
            public boolean test(PinIn p, String s1, String s2) {
                return p.contains(s1, s2);
            }

            @Override
            public boolean raw(String s1, String s2) {
                return s1.contains(s2);
            }
        },

        EQUAL {
            @Override
            public boolean test(Accelerator a, int offset, int start) {
                return a.matches(offset, start);
            }

            @Override
            public boolean test(PinIn p, String s1, String s2) {
                return p.matches(s1, s2);
            }

            @Override
            public boolean raw(String s1, String s2) {
                return s1.equals(s2);
            }
        };

        public boolean test(Accelerator a, int offset, int start) {
            return false;
        }

        public boolean test(PinIn p, String s1, String s2) {
            return false;
        }

        public boolean raw(String s1, String s2) {
            return false;
        }
    }
}
