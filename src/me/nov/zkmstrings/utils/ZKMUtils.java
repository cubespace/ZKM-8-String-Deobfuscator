package me.nov.zkmstrings.utils;

import java.util.ArrayList;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import me.lpk.antis.impl.AntiVertex.Deobfuscated;
import me.lpk.util.OpUtils;
import me.nov.zkmstrings.Deobfuscation;

public class ZKMUtils implements Opcodes {
  /**
   * @param mn
   *          ZKM decryption method
   */
  public static MethodNode cutClinit(MethodNode mn) {
    // need to check before invoking scan for end of decryption
    AbstractInsnNode first = mn.instructions.getFirst();
    LabelNode end = null;
    while (first != null) {
      if (first.getOpcode() == PUTSTATIC) {
        FieldInsnNode fin = (FieldInsnNode) first;
        if (fin.desc.equals("[Ljava/lang/String;")) { // zkm stores the stringarray at the end of decryption
          if (first.getNext() != null) {
            AbstractInsnNode next = first.getNext();
            if (next.getOpcode() == GOTO) {
              end = ((JumpInsnNode) next).label;
              break;
            } else if (next.getType() == AbstractInsnNode.LABEL) {
              end = (LabelNode) next;
              break;
            }
            // continue searching
          } else {
            // nothing to do, method is only zkm decrypt (unusual case)
            return mn;
          }
        }
      }
      first = first.getNext();
    }

    if (end == null)
      return mn; // no end of code found, returning original method
    MethodNode init = new MethodNode(ACC_PUBLIC | ACC_STATIC, "init_zkm", "()V", null, null); // construct new method for decryption
    init.instructions = MethodUtils.clone(mn.instructions, end);
    init.localVariables = null;
    init.maxLocals = 8;
    init.maxStack = 16;
    if (Deobfuscation.REMOVE_STATICINVK) {
      for (AbstractInsnNode ain : init.instructions.toArray()) {
        if (ain.getOpcode() == INVOKESTATIC) {
          init.instructions.set(ain, new InsnNode(POP));
        }
      }
    }
    return init;
  }

  /**
   * @param mn
   *          MethodNode to check if is zkm clinit decryption method
   */
  public static boolean isZKMClinit(MethodNode mn) {
    if (mn.name.equals("<clinit>") && mn.instructions.size() > 20) {
      AbstractInsnNode first = mn.instructions.getFirst();
      boolean correct = (first.getType() == AbstractInsnNode.INT_INSN || first.getType() == AbstractInsnNode.INSN
          || first.getType() == AbstractInsnNode.LDC_INSN);
      if (correct && first.getNext().getOpcode() == ANEWARRAY) {
        // most likely zkm clinit
        return true;
      }
    }
    return false;
  }

  /**
   * @param mn
   *          MethodNode to check if is zkm decryption method (2x int)
   */
  public static boolean isZKMDecrypt(MethodNode mn) {
    if (mn.instructions.size() > 20) {
      AbstractInsnNode first = mn.instructions.getFirst();
      boolean correct = (first.getType() == AbstractInsnNode.VAR_INSN);
      if (correct && InstructionUtils.isNumber(first.getNext())) {
        // most likely zkm decr
        return true;
      }
    }
    return false;
  }

  /**
   * @param mn
   *          ZKM decryption method
   */
  public static String[] getZKMArrayNames(MethodNode mn) {
    String[] names = new String[2];
    int i = 0;
    for (AbstractInsnNode ain : mn.instructions.toArray()) {
      if (ain.getOpcode() == PUTSTATIC) {
        FieldInsnNode fin = (FieldInsnNode) ain;
        if (fin.desc.equals("[Ljava/lang/String;")) {
          if (i == 2)
            continue; //too many entries
          names[i++] = fin.name;
        }
      }
    }
    return names;
  }

  /**
   * Creates a ClassNode specified for invocation
   * 
   * @param cn
   *          With ZKM obfuscated ClassNode
   */
  public static ClassNode generateInvocation(ClassNode cn) {
    ClassNode decryptNode = new ClassNode();
    decryptNode.name = "zkm_obfuscated";
    decryptNode.superName = "java/lang/Object";
    decryptNode.version = 49;
    decryptNode.access = 1;
    for (MethodNode mn : cn.methods) {
      if (mn.name.equals("<clinit>") && isZKMClinit(mn)) {
        // found decryption node
        String[] zkmArrayNames = getZKMArrayNames(mn);
        decryptNode.methods.add(renameRefs(cutClinit(mn), decryptNode.name));
        decryptNode.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, zkmArrayNames[0], "[Ljava/lang/String;", null, null));
        if (!Deobfuscation.SCND_METHOD) {
          decryptNode.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, zkmArrayNames[1], "[Ljava/lang/String;", null, null));
        }
      }
      if (!Deobfuscation.SCND_METHOD) {
        if (mn.desc.equals("(II)Ljava/lang/String;") && isZKMDecrypt(mn)) {
          decryptNode.methods.add(renameRefs(cloneDecrypt(mn), decryptNode.name));
        }
      }
    }
    return decryptNode;
  }

  private static MethodNode cloneDecrypt(MethodNode mn) {
    MethodNode decrypt = new MethodNode(ACC_PUBLIC | ACC_STATIC, "decrypt_array", "(II)Ljava/lang/String;", null, null);
    decrypt.instructions = MethodUtils.clone(mn.instructions, null);
    decrypt.localVariables = null;
    decrypt.maxLocals = 9;
    decrypt.maxStack = 9;
    return decrypt;
  }

  /**
   * Renames all String[] references
   * 
   */
  private static MethodNode renameRefs(MethodNode mn, String owner) {
    AbstractInsnNode ain = mn.instructions.getFirst();
    int i = 0;
    while (ain != null) {
      if (ain.getType() == AbstractInsnNode.FIELD_INSN) {
        FieldInsnNode fin = (FieldInsnNode) ain;
        if (fin.desc.equals("[Ljava/lang/String;")) {
          fin.owner = owner; // rename owner
        }
      }
      ain = ain.getNext();
    }
    return mn;
  }
}
