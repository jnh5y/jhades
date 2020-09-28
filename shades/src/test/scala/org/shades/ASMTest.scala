package org.shades

import java.io.PrintWriter

import org.junit.runner.RunWith
import org.objectweb.asm.util.{Printer, Textifier, TraceClassVisitor, TraceMethodVisitor}
import org.objectweb.asm.{ClassReader, ClassVisitor, Handle, MethodVisitor, Opcodes}
import org.shades.MyMethodVisitor.methodSignatures
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConversions.asScalaBuffer
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

      println(s"List of methods called:")
      MyMethodVisitor.methodSignatures.toSeq.sorted.foreach { println }

      ok
    }
  }
}

class ClassPrinter extends ClassVisitor(Opcodes.ASM7) {
  val p = new Textifier()
  val myMethodVisitor = new MyMethodVisitor

  override def visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String,
                     superName: String,
                     interfaces: Array[String]): Unit = {
    println(s"Visited $name with signature $signature")
  }
  override def visitMethod(access: Int,
                           name: String,
                           descriptor: String,
                           signature: String,
                           exceptions: Array[String]): MethodVisitor = {
    println(s"Visited method: $name with descriptor $descriptor with signature $signature")
    //super.visitMethod(access, name, descriptor, signature, exceptions)
//    val pw = new PrintWriter(System.out)

    val methodPrinter = p.visitMethod(access, name, descriptor, signature, exceptions)
//    myMethodVisitor
    new MyMethodVisitor //TraceMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), methodPrinter)
  //  new MyTraceMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), methodPrinter)

  }
}

class MyTraceMethodVisitor(mv: MethodVisitor, p: Printer) extends MethodVisitor(Opcodes.ASM7) {
  val delegate = new TraceMethodVisitor(mv, p)
  override def visitInvokeDynamicInsn(name: String,
                                      descriptor: String,
                                      bootstrapMethodHandle: Handle,
                                      bootstrapMethodArguments: Object*): Unit = {
    println(s"""VisitInvokeDynamic $name $descriptor $bootstrapMethodHandle ${bootstrapMethodArguments.mkString(", ")}""")
    delegate.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments: _*)
  }
}

class MyMethodVisitor extends MethodVisitor(Opcodes.ASM7) {
  override def visitInvokeDynamicInsn(name: String,
                                      descriptor: String,
                                      bootstrapMethodHandle: Handle,
                                      bootstrapMethodArguments: Object*): Unit = {
    println(s"""VisitInvokeDynamic $name $descriptor $bootstrapMethodHandle ${bootstrapMethodArguments.mkString(", ")}""")
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments: _*)
  }

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
    methodSignatures.add(MethodSignature(owner, name, descriptor))
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
  }
}

object MyMethodVisitor {
  val methodSignatures : mutable.Set[MethodSignature] = new mutable.HashSet[MethodSignature]()
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



class MyTextifer extends Textifier(Opcodes.ASM7) {
  override def visitInvokeDynamicInsn(name: String,
                                      descriptor: String,
                                      bootstrapMethodHandle: Handle,
                                      bootstrapMethodArguments: Object*): Unit = {
      println(s"""VisitInvokeDynamic $name $descriptor $bootstrapMethodHandle ${bootstrapMethodArguments.mkString(", ")}""")
      super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments: _*)
    }

  override def createTextifier(): Textifier = new MyTextifer
}
