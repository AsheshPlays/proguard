/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2022 Guardsquare NV
 */

package proguard.util.kotlin.asserter

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.spyk
import io.mockk.verify
import proguard.AppView
import proguard.Configuration
import proguard.classfile.Clazz
import proguard.classfile.ProgramClass
import proguard.classfile.kotlin.KotlinClassKindMetadata
import proguard.classfile.kotlin.KotlinMetadata
import proguard.classfile.kotlin.visitor.KotlinMetadataVisitor
import proguard.resources.file.ResourceFilePool
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource

class KotlinMetadataAsserterTest : FreeSpec({
    "Given an interface with default implementation" - {
        val (programClassPool, libraryClassPool) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                interface Test {
                    fun foo() {
                        println("DEFAULT")
                    }
                }
                """.trimIndent()
            )
        )

        "When the KotlinMetadataAsserter is run" - {
            val appView = AppView(programClassPool, libraryClassPool, ResourceFilePool(), null)
            KotlinMetadataAsserter(Configuration()).execute(appView)
            "Then the metadata should not be thrown away" {
                val visitor = spyk<KotlinMetadataVisitor>()
                programClassPool.classesAccept("Test") {
                    it.kotlinMetadataAccept(visitor)
                }

                verify(exactly = 1) {
                    visitor.visitKotlinClassMetadata(
                        programClassPool.getClass("Test"),
                        ofType<KotlinClassKindMetadata>()
                    )
                }
            }
        }
    }

    "Given an interface with default implementation and missing \$DefaultImpls class" - {
        val (programClassPool, libraryClassPool) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                interface Test {
                    fun foo() {
                        println("DEFAULT")
                    }
                }
                """.trimIndent()
            )
        )

        "When the KotlinMetadataAsserter is run" - {
            val appView = AppView(programClassPool, libraryClassPool, ResourceFilePool(), null)

            programClassPool.removeClass("Test\$DefaultImpls")

            KotlinMetadataAsserter(Configuration()).execute(appView)

            "Then the metadata should be thrown away" {
                val visitor = spyk<KotlinMetadataVisitor>()
                programClassPool.classesAccept("Test") {
                    it.kotlinMetadataAccept(visitor)
                }

                verify(exactly = 0) {
                    visitor.visitKotlinClassMetadata(
                        programClassPool.getClass("Test"),
                        ofType<KotlinClassKindMetadata>()
                    )
                }
            }
        }
    }

    "Given an interface with default implementation using Java 8+ default methods" - {
        val (programClassPool, libraryClassPool) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                interface Test {
                    fun foo() {
                        println("DEFAULT")
                    }
                }
                """.trimIndent()
            ),
            kotlincArguments = listOf("-Xjvm-default=all")
        )

        "When the KotlinMetadataAsserter is run" - {
            val appView = AppView(programClassPool, libraryClassPool, ResourceFilePool(), null)
            KotlinMetadataAsserter(Configuration()).execute(appView)
            "Then the metadata should not be thrown away" {
                val visitor = spyk<KotlinMetadataVisitor>()
                programClassPool.classesAccept("Test") {
                    it.kotlinMetadataAccept(visitor)
                }

                verify(exactly = 1) {
                    visitor.visitKotlinClassMetadata(
                        programClassPool.getClass("Test"),
                        ofType<KotlinClassKindMetadata>()
                    )
                }
            }
        }
    }

    "Given an enum" - {
        val (programClassPool, libraryClassPool) = ClassPoolBuilder.fromSource(

            KotlinSource(
                "Test.kt",
                """
                enum class Test {
                    CENTER,
                    BOTTOM
                }
                """.trimIndent()
            )

        )

        "When the referencedEnumEntries are set to null" - {
            val visitor = spyk(
                object : KotlinMetadataVisitor {
                    override fun visitAnyKotlinMetadata(clazz: Clazz?, kotlinMetadata: KotlinMetadata?) {}

                    override fun visitKotlinClassMetadata(
                        clazz: Clazz?,
                        kotlinClassKindMetadata: KotlinClassKindMetadata?
                    ) {
                        kotlinClassKindMetadata?.referencedEnumEntries = listOf(null, null)
                    }
                })
            programClassPool.classesAccept("Test") {
                it.kotlinMetadataAccept(visitor)
            }

            "Then the KotlinMetadataAsserter should throw away the enum metadata" {
                (programClassPool.getClass("Test") as ProgramClass).kotlinMetadata shouldNotBe null
                val appView = AppView(programClassPool, libraryClassPool, ResourceFilePool(), null)
                // KotlinMetadataAsserter should remove Test enum's metadata because
                // null entries of kotlinClassKindMetadata.referencedEnumEntries violates the ClassIntegrity.
                KotlinMetadataAsserter(Configuration()).execute(appView)
                (programClassPool.getClass("Test") as ProgramClass).kotlinMetadata shouldBe null
            }
        }
    }
})
