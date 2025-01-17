/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.kapt.incremental

import java.io.*
import java.util.*
import kotlin.collections.HashMap

private const val CLASSPATH_ENTRIES_FILE = "classpath-entries.bin"
private const val CLASSPATH_STRUCTURE_FILE = "classpath-structure.bin"

open class ClasspathSnapshot protected constructor(
    private val cacheDir: File,
    private val classpath: List<File>,
    private val dataForFiles: MutableMap<File, ClasspathEntryData?>
) {
    object ClasspathSnapshotFactory {
        fun loadFrom(cacheDir: File): ClasspathSnapshot {
            val classpathEntries = cacheDir.resolve(CLASSPATH_ENTRIES_FILE)
            val classpathStructureData = cacheDir.resolve(CLASSPATH_STRUCTURE_FILE)
            if (!classpathEntries.exists() || !classpathStructureData.exists()) {
                return UnknownSnapshot
            }

            val classpathFiles = ObjectInputStream(BufferedInputStream(classpathEntries.inputStream())).use {
                @Suppress("UNCHECKED_CAST")
                it.readObject() as List<File>
            }

            val dataForFiles =
                ObjectInputStream(BufferedInputStream(classpathStructureData.inputStream())).use {
                    @Suppress("UNCHECKED_CAST")
                    it.readObject() as MutableMap<File, ClasspathEntryData?>
                }
            return ClasspathSnapshot(cacheDir, classpathFiles, dataForFiles)
        }

        fun createCurrent(cacheDir: File, classpath: List<File>, allStructureData: Set<File>): ClasspathSnapshot {
            val data = allStructureData.associateTo(HashMap<File, ClasspathEntryData?>(allStructureData.size)) { it to null }

            return ClasspathSnapshot(cacheDir, classpath, data)
        }
    }

    private fun isCompatible(snapshot: ClasspathSnapshot) =
        this != UnknownSnapshot && snapshot != UnknownSnapshot && classpath == snapshot.classpath

    /** Compare this snapshot with the specified one only for the specified files. */
    fun diff(previousSnapshot: ClasspathSnapshot, changedFiles: Set<File>): KaptClasspathChanges {
        if (!isCompatible(previousSnapshot)) {
            return KaptClasspathChanges.Unknown
        }

        loadEntriesFor(changedFiles)

        val currentHashAbiSize = changedFiles.sumBy { dataForFiles[it]!!.classAbiHash.size }
        val currentHashesToAnalyze =
            HashMap<String, ByteArray>(currentHashAbiSize).also { hashes ->
                changedFiles.forEach {
                    hashes.putAll(dataForFiles[it]!!.classAbiHash)
                }
            }

        val currentUnchanged = dataForFiles.keys.filter { it !in changedFiles }
        val previousChanged = previousSnapshot.dataForFiles.keys.filter { it !in currentUnchanged }

        check(changedFiles.size == previousChanged.size) {
            """
            Number of changed files in snapshots differs. Reported changed files: $changedFiles
            Current snapshot data files: ${dataForFiles.keys}
            Previous snapshot data files: ${previousSnapshot.dataForFiles.keys}
        """.trimIndent()
        }

        val previousHashAbiSize = previousChanged.sumBy { previousSnapshot.dataForFiles.get(it)?.classAbiHash?.size ?: 0 }
        val previousHashesToAnalyze =
            HashMap<String, ByteArray>(previousHashAbiSize).also { hashes ->
                for (c in previousChanged) {
                    previousSnapshot.dataForFiles[c]?.let {
                        hashes.putAll(it.classAbiHash)
                    }
                }
            }

        val changedClasses = mutableSetOf<String>()
        for (key in previousHashesToAnalyze.keys + currentHashesToAnalyze.keys) {
            val previousHash = previousHashesToAnalyze[key]
            if (previousHash == null) {
                changedClasses.add(key)
                continue
            }
            val currentHash = currentHashesToAnalyze[key]
            if (currentHash == null) {
                changedClasses.add(key)
                continue
            }
            if (!previousHash.contentEquals(currentHash)) {
                changedClasses.add(key)
            }
        }

        // We do not compute structural data for unchanged files of the current snapshot for performance reasons.
        // That is why we reuse the previous snapshot as that one contains all unchanged entries.
        for (unchanged in currentUnchanged) {
            dataForFiles[unchanged] = previousSnapshot.dataForFiles[unchanged]!!
        }

        val allImpactedClasses = findAllImpacted(changedClasses)

        return KaptClasspathChanges.Known(allImpactedClasses)
    }

    private fun loadEntriesFor(file: Iterable<File>) {
        for (f in file) {
            if (dataForFiles[f] == null) {
                dataForFiles[f] = ClasspathEntryData.ClasspathEntrySerializer.loadFrom(f)
            }
        }
    }

    private fun loadAll() {
        loadEntriesFor(dataForFiles.keys)
    }

    fun writeToCache() {
        loadAll()

        val classpathEntries = cacheDir.resolve(CLASSPATH_ENTRIES_FILE)
        ObjectOutputStream(BufferedOutputStream(classpathEntries.outputStream())).use {
            it.writeObject(classpath)
        }

        val classpathStructureData = cacheDir.resolve(CLASSPATH_STRUCTURE_FILE)
        ObjectOutputStream(BufferedOutputStream(classpathStructureData.outputStream())).use {
            it.writeObject(dataForFiles)
        }
    }

    private fun findAllImpacted(changedClasses: Set<String>): Set<String> {
        // TODO (gavra): Avoid building all reverse lookups. Most changes are local to the classpath entry, use that.
        val transitiveDeps = HashMap<String, MutableList<String>>()
        val nonTransitiveDeps = HashMap<String, MutableList<String>>()

        for (entry in dataForFiles.values) {
            for ((className, classDependency) in entry!!.classDependencies) {
                for (abiType in classDependency.abiTypes) {
                    (transitiveDeps[abiType] ?: LinkedList()).let {
                        it.add(className)
                        transitiveDeps[abiType] = it
                    }
                }
                for (privateType in classDependency.privateTypes) {
                    (nonTransitiveDeps[privateType] ?: LinkedList()).let {
                        it.add(className)
                        nonTransitiveDeps[privateType] = it
                    }

                }
            }
        }

        val allImpacted = mutableSetOf<String>()
        var current = changedClasses
        while (current.isNotEmpty()) {
            val newRound = mutableSetOf<String>()
            for (klass in current) {
                if (allImpacted.add(klass)) {
                    transitiveDeps[klass]?.let {
                        newRound.addAll(it)
                    }

                    nonTransitiveDeps[klass]?.let {
                        allImpacted.addAll(it)
                    }
                }
            }
            current = newRound
        }

        return allImpacted
    }
}

object UnknownSnapshot : ClasspathSnapshot(File(""), emptyList(), mutableMapOf())

sealed class KaptIncrementalChanges {
    object Unknown : KaptIncrementalChanges()
    class Known(val changedSources: Set<File>, val changedClasspathJvmNames: Set<String>) : KaptIncrementalChanges()
}

sealed class KaptClasspathChanges {
    object Unknown : KaptClasspathChanges()
    class Known(val names: Set<String>) : KaptClasspathChanges()
}