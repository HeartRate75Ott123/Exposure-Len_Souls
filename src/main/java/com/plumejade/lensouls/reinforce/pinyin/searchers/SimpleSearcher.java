// Vendored third-party library: PinIn by Towdium (https://github.com/Towdium/PinIn), MIT License.
// Package renamed to com.plumejade.lensouls.reinforce.pinyin and kept self-contained (only java.* and fastutil from Minecraft).
// Originally vendored by JustEnoughCharacters / Remorphed as plume.summoner.pinin; see docs/ for details.
package com.plumejade.lensouls.reinforce.pinyin.searchers;

import it.unimi.dsi.fastutil.ints.IntList;
import com.plumejade.lensouls.reinforce.pinyin.PinIn;
import com.plumejade.lensouls.reinforce.pinyin.utils.Accelerator;
import com.plumejade.lensouls.reinforce.pinyin.utils.Compressor;

import java.util.ArrayList;
import java.util.List;

public class SimpleSearcher<T> implements Searcher<T> {
    List<T> objs = new ArrayList<>();
    final Accelerator acc;
    final Compressor strs = new Compressor();
    final PinIn context;
    final Logic logic;
    final PinIn.Ticket ticket;

    public SimpleSearcher(Logic logic, PinIn context) {
        this.context = context;
        this.logic = logic;
        acc = new Accelerator(context);
        acc.setProvider(strs);
        ticket = context.ticket(this::reset);
    }

    @Override
    public void put(String name, T identifier) {
        strs.put(name);
        for (int i = 0; i < name.length(); i++)
            context.getChar(name.charAt(i));
        objs.add(identifier);
    }

    @Override
    public List<T> search(String name) {
        List<T> ret = new ArrayList<>();
        acc.search(name);
        IntList offsets = strs.offsets();
        for (int i = 0; i < offsets.size(); i++) {
            int s = offsets.getInt(i);
            if (logic.test(acc, 0, s)) ret.add(objs.get(i));
        }
        return ret;
    }

    @Override
    public PinIn context() {
        return context;
    }

    public void reset() {
        acc.reset();
    }
}
