package org.shades

import org.junit.runner.RunWith
import org.objectweb.asm.{ClassReader, ClassVisitor, MethodVisitor, Opcodes}
import org.shades.MethodSignature.calledMethodSignatures
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class ASMTest extends Specification {

  "ASM" should {
    "do what I want" in {
      val cr = new ClassReader("java.lang.Thread")

      val cv = new ClassPrinter
//      val pw = new PrintWriter(System.out)
//      val cv = new TraceClassVisitor(pw)
      cr.accept(cv, 0)
      //println(s"""Textifier: ${cv.p.text.mkString("\n")}""")

      println(s"List of methods provided:")
      MethodSignature.providedMethodSignatures.toSeq.sorted.foreach { println }

      println(s"List of methods called:")
      MethodSignature.calledMethodSignatures.toSeq.sorted.foreach { println }

      ok
    }
  }
}

class ClassPrinter extends ClassVisitor(Opcodes.ASM7) {
  val myMethodVisitor = new MyMethodVisitor
  var name = ""

  override def visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String,
                     superName: String,
                     interfaces: Array[String]): Unit = {
    println(s"Visited $name with signature $signature")
    this.name = name
  }
  override def visitMethod(access: Int,
                           name: String,
                           descriptor: String,
                           signature: String,
                           exceptions: Array[String]): MethodVisitor = {
    println(s"Visited method: $name with descriptor $descriptor with signature $signature")
    MethodSignature.providedMethodSignatures.add(MethodSignature(this.name, name, descriptor))
    new MyMethodVisitor //TraceMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), methodPrinter)
  }
}

class MyMethodVisitor extends MethodVisitor(Opcodes.ASM7) {
  override def visitMethodInsn(opcode: Int,
                               owner: String,
                               name: String,
                               descriptor: String,
                               isInterface: Boolean): Unit = {
    println(s"visitMethod $opcode $owner $name $descriptor $isInterface")
//    "INVOKEVIRTUAL", // 182 (0xb6)
//    "INVOKESPECIAL", // 183 (0xb7)
//    "INVOKESTATIC", // 184 (0xb8)
//    "INVOKEINTERFACE", // 185 (0xb9)
//    "INVOKEDYNAMIC", // 186 (0xba) // Different method
    calledMethodSignatures.add(MethodSignature(owner, name, descriptor))
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
  }
}

object MethodSignature {
  val providedMethodSignatures : mutable.Set[MethodSignature] = new mutable.HashSet[MethodSignature]()
  val calledMethodSignatures : mutable.Set[MethodSignature] = new mutable.HashSet[MethodSignature]()
}

case class MethodSignature(owner: String, name: String, descriptor: String) extends Comparable[MethodSignature] {
  override def compareTo(o: MethodSignature): Int = {
    val comp = this.owner.compareTo(o.owner)
    if (comp != 0) {
      comp
    } else {
      val comp2 = this.name.compareTo(o.name)
      if (comp2 != 0) {
        comp2
      } else
        this.descriptor.compareTo(o.descriptor)
    }
  }
}
