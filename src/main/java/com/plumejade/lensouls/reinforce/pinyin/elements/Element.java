// Vendored third-party library: PinIn by Towdium (https://github.com/Towdium/PinIn), MIT License.
// Package renamed to com.plumejade.lensouls.reinforce.pinyin and kept self-contained (only java.* and fastutil from Minecraft).
// Originally vendored by JustEnoughCharacters / Remorphed as plume.summoner.pinin; see docs/ for details.
package com.plumejade.lensouls.reinforce.pinyin.elements;

import com.plumejade.lensouls.reinforce.pinyin.utils.IndexSet;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public interface Element {
    IndexSet match(String str, int start, boolean partial);
}
