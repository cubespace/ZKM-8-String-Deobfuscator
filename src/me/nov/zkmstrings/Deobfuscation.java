package me.nov.zkmstrings;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import me.lpk.analysis.Sandbox;
import me.lpk.util.AccessHelper;
import me.nov.zkmstrings.utils.InstructionUtils;
import me.nov.zkmstrings.utils.ZKMUtils;

public class Deobfuscation implements Opcodes {

  private Map<String, ClassNode> classes;
  private boolean success;
  public final static boolean SCND_METHOD = true;
  public final static boolean REMOVE_STATICINVK = true;

  public Deobfuscation(Map<String, ClassNode> classes) {
    this.classes = classes;
    this.success = false;
  }

  public boolean isSuccess() {
    return success;
  }

  public void start() {
    try {
      PrintWriter pw = new PrintWriter("strings.txt");
      for (ClassNode cn : classes.values()) {
        ClassNode invocationNode = ZKMUtils.generateInvocation(cn);
        if (invocationNode == null || invocationNode.methods.size() < (SCND_METHOD ? 1 : 2)) {
          continue;
        }
        Class<?> loaded = null;
        try {
          loaded = Sandbox.load(invocationNode);
          Method clinit = loaded.getMethod("init_zkm");
          clinit.invoke(null); // invoke decryption
        } catch (Exception e) {
          System.out.println(e.toString());
          continue;
        }
        if (SCND_METHOD) {
          String[] decrypted = null;
          Field array = loaded.getDeclaredField(invocationNode.fields.get(0).name);
          decrypted = (String[]) array.get(null);
          if (decrypted != null) {
            for (MethodNode mn : cn.methods) { // find decrypt calls
              for (AbstractInsnNode ain : mn.instructions.toArray()) {
                if (ain.getOpcode() == GETSTATIC) {
                  FieldInsnNode fin = (FieldInsnNode) ain;
                  if (fin.name.equals(array.getName()) && fin.desc.equals("[Ljava/lang/String;")) {
                    AbstractInsnNode next = fin.getNext();
                    while (next != null && next instanceof VarInsnNode) {
                      next = next.getNext();
                    }
                    if (next != null && InstructionUtils.isNumber(next) && next.getNext().getOpcode() == AALOAD) {
                      int indx = InstructionUtils.getIntValue(next);
                      AbstractInsnNode aaload = next.getNext();
                      mn.instructions.insert(aaload, new LdcInsnNode(decrypted[indx]));
                      System.out.println(decrypted[indx]);
                      pw.println(decrypted[indx]);
                      mn.instructions.insert(aaload, new InsnNode(POP));
                    }
                  }
                }
              }
            }
          }
          continue;
        }
        // Field array = loaded.getFields()[0];
        // String[] decrypted = (String[]) array.get(null); // get results

        Method decrypt = loaded.getMethod("decrypt_array", new Class[] { int.class, int.class });
        for (MethodNode mn : cn.methods) { // find decrypt calls
          for (AbstractInsnNode ain : mn.instructions.toArray()) {
            if (ain.getOpcode() == INVOKESTATIC) {
              MethodInsnNode min = (MethodInsnNode) ain;
              if (min.desc.equals("(II)Ljava/lang/String;") && min.owner.equals(cn.name) && InstructionUtils.isNumber(min.getPrevious())
                  && InstructionUtils.isNumber(min.getPrevious().getPrevious())) {
                int nr1 = InstructionUtils.getIntValue(min.getPrevious().getPrevious());
                int nr2 = InstructionUtils.getIntValue(min.getPrevious());
                String decryptedStr = (String) decrypt.invoke(null, nr1, nr2);
                if (decryptedStr != null) {
                  mn.instructions.remove(min.getPrevious().getPrevious());
                  mn.instructions.remove(min.getPrevious());
                  mn.instructions.set(ain, new LdcInsnNode(decryptedStr));
                  System.out.println(decryptedStr);
                }
                pw.println(decryptedStr);
              }
            }
          }
        }
      }
      success = true;
      pw.close();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

}
