// Vendored third-party library: PinIn by Towdium (https://github.com/Towdium/PinIn), MIT License.
// Package renamed to com.plumejade.lensouls.reinforce.pinyin and kept self-contained (only java.* and fastutil from Minecraft).
// Originally vendored by JustEnoughCharacters / Remorphed as plume.summoner.pinin; see docs/ for details.
package com.plumejade.lensouls.reinforce.pinyin.elements;

import com.plumejade.lensouls.reinforce.pinyin.utils.IndexSet;

public class Char implements Element {
    protected char ch;
    public static final Pinyin[] NONE = new Pinyin[0];

    Pinyin[] pinyin;

    public Char(char ch, Pinyin[] pinyin) {
        this.ch = ch;
        this.pinyin = pinyin;
    }

    @Override
    public IndexSet match(String str, int start, boolean partial) {
        IndexSet ret = (str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE).copy();
        for (Element p : pinyin) ret.merge(p.match(str, start, partial));
        return ret;
    }

    public char get() {
        return ch;
    }

    public Pinyin[] pinyins() {
        return pinyin;
    }

    public static class Dummy extends Char {
        public Dummy() {
            super('\0', NONE);
        }

        public void set(char ch) {
            this.ch = ch;
        }
    }
}
