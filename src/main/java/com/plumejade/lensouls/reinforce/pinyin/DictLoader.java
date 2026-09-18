// Vendored third-party library: PinIn by Towdium (https://github.com/Towdium/PinIn), MIT License.
// Package renamed to com.plumejade.lensouls.reinforce.pinyin and kept self-contained (only java.* and fastutil from Minecraft).
// Originally vendored by JustEnoughCharacters / Remorphed as plume.summoner.pinin; see docs/ for details.
package com.plumejade.lensouls.reinforce.pinyin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface DictLoader {
    void load(BiConsumer<Character, String[]> feed);

    class Default implements DictLoader {
        @Override
        public void load(BiConsumer<Character, String[]> feed) {
            InputStream is = PinIn.class.getResourceAsStream("data.txt");
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    char ch = line.charAt(0);
                    String[] records = line.substring(3).split(", ");
                    feed.accept(ch, records);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
