package me.nov.zkmstrings;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import me.lpk.util.JarUtils;

public class ZKMDeobf {

	public static void main(String[] args) throws IOException {
		File input = new File("input.jar");
		if(!input.exists()) System.out.println("No input.jar found!");
		Map<String, ClassNode> classes = JarUtils.loadClasses(input);
		Map<String, byte[]> out = JarUtils.loadNonClassEntries(input);
		System.out.println("You may need to adjust the booleans in Deobfuscation.java");
		Deobfuscation de = new Deobfuscation(classes);
		de.start();

		if (de.isSuccess()) {
			for (ClassNode cn : classes.values()) {
				ClassWriter cw = new ClassWriter(0);
				cn.accept(cw);
				out.put(cn.name, cw.toByteArray());
			}
			JarUtils.saveAsJar(out, "output.jar");
			System.out.println("Finished with success!");
		} else {
			System.err.println("Finished without success.");
		}
	}

}
