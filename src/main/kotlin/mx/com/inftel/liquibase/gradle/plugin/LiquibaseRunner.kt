/*
 *    Copyright 2019 Santos Zatarain Vera (coder.santoszv_at_gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package mx.com.inftel.liquibase.gradle.plugin

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.diff.output.DiffOutputControl
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.FileSystemResourceAccessor
import org.gradle.api.Project
import java.net.URL
import java.net.URLClassLoader

class LiquibaseClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {

    private val prefix = LiquibaseRunner::class.qualifiedName!!

    @Synchronized
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        return if (name.startsWith(prefix)) {
            val c: Class<*> = findLoadedClass(name) ?: findClass(name)
            if (resolve) {
                resolveClass(c)
            }
            return c
        } else {
            super.loadClass(name, resolve)
        }
    }
}

class LiquibaseRunner(private val project: Project, private val command: LiquibaseCommandEnum, private val parameters: LiquibaseParameters) : Runnable {

    private val classLoaderResourceAccessor: ClassLoaderResourceAccessor by lazy {
        ClassLoaderResourceAccessor(Thread.currentThread().contextClassLoader)
    }
    private val fileSystemResourceAccessor: FileSystemResourceAccessor by lazy {
        FileSystemResourceAccessor(project.file(parameters.changeLogDirectory).absolutePath)
    }

    private val database: Database by lazy {
        CommandLineUtils.createDatabaseObject(
                classLoaderResourceAccessor,
                parameters.url,
                parameters.username,
                parameters.password,
                parameters.driver,
                parameters.defaultCatalogName,
                parameters.defaultSchemaName,
                parameters.outputDefaultCatalog,
                parameters.outputDefaultSchema,
                parameters.databaseClass,
                if (parameters.driverPropertiesFile != null) project.file(parameters.driverPropertiesFile!!).absolutePath else null,
                parameters.propertyProviderClass,
                parameters.liquibaseCatalogName,
                parameters.liquibaseSchemaName,
                parameters.databaseChangeLogTableName,
                parameters.databaseChangeLogTableNameLockTable
        )
    }
    private val instance: Liquibase by lazy {
        Liquibase(parameters.changeLogFile, fileSystemResourceAccessor, database)
    }

    override fun run() {
        when (command) {
            // Database Update Commands
            LiquibaseCommandEnum.DatabaseUpdate -> update()
            LiquibaseCommandEnum.DatabaseUpdateCount -> updateCount()
            LiquibaseCommandEnum.DatabaseUpdateSQL -> updateSQL()
            LiquibaseCommandEnum.DatabaseUpdateCountSQL -> updateCountSQL()
            // Database Rollback Commands
            LiquibaseCommandEnum.DatabaseRollback -> rollback()
            //LiquibaseCommandEnum.RollbackToDate -> rollbackToDate()
            LiquibaseCommandEnum.DatabaseRollbackCount -> rollbackCount()
            LiquibaseCommandEnum.DatabaseRollbackSQL -> rollbackSQL()
            //LiquibaseCommandEnum.RollbackToDateSQL -> rollbackToDateSQL()
            LiquibaseCommandEnum.DatabaseRollbackCountSQL -> rollbackCountSQL()
            //LiquibaseCommandEnum.FutureRollbackSQL -> futureRollbackSQL()
            //LiquibaseCommandEnum.UpdateTestingRollback -> updateTestingRollback()
            LiquibaseCommandEnum.DatabaseGenerateChangeLog -> generateChangeLog()
            // Documentation Commands
            LiquibaseCommandEnum.DatabaseDoc -> dbDoc()
            // Maintenance Commands
            LiquibaseCommandEnum.DatabaseTag -> tag()
            LiquibaseCommandEnum.DatabaseTagExists -> tagExists()
            LiquibaseCommandEnum.DatabaseStatus -> status()
            LiquibaseCommandEnum.DatabaseValidate -> validate()
            LiquibaseCommandEnum.DatabaseChangelogSync -> changelogSync()
            LiquibaseCommandEnum.DatabaseChangelogSyncSQL -> changelogSyncSQL()
            //LiquibaseCommandEnum.MarkNextChangeSetRan -> markNextChangeSetRan()
            LiquibaseCommandEnum.DatabaseListLocks -> listLocks()
            LiquibaseCommandEnum.DatabaseReleaseLocks -> releaseLocks()
            LiquibaseCommandEnum.DatabaseDropAll -> dropAll()
            LiquibaseCommandEnum.DatabaseClearCheckSums -> clearCheckSums()
        }
    }

    private fun update() {
        try {
            instance.update(
                    parameters.contexts
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun updateCount() {
        require(parameters.changesToApply > 0) {
            "Liquibase: Changes to apply must be greater than zero."
        }
        try {
            instance.update(
                    parameters.changesToApply,
                    parameters.contexts
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun updateSQL() {
        val outputFile = project.file(parameters.outputUpdateSQL)
        outputFile.parentFile.mkdirs()
        try {
            outputFile.bufferedWriter().use {
                instance.update(
                        parameters.contexts,
                        it
                )
                it.flush()
            }
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun updateCountSQL() {
        require(parameters.changesToApply > 0) {
            "Liquibase: Changes to apply must be greater than zero."
        }
        val outputFile = project.file(parameters.outputUpdateSQL)
        outputFile.parentFile.mkdirs()
        try {
            outputFile.bufferedWriter().use {
                instance.update(
                        parameters.changesToApply,
                        parameters.contexts,
                        it
                )
                it.flush()
            }
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun rollback() {
        try {
            instance.rollback(
                    parameters.tagToRollBackTo ?: throw IllegalArgumentException("Liquibase: Tag to rollback is required."),
                    parameters.contexts
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun rollbackCount() {
        require(parameters.changesToRollback > 0) {
            "Liquibase: Changes to rollback must be greater than zero."
        }
        try {
            instance.rollback(
                    parameters.changesToRollback,
                    parameters.contexts
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun rollbackSQL() {
        val outputFile = project.file(parameters.outputRollbackSQL)
        outputFile.parentFile.mkdirs()
        try {
            outputFile.bufferedWriter().use {
                instance.rollback(
                        parameters.tagToRollBackTo ?: throw IllegalArgumentException("Liquibase: Tag to rollback is required."),
                        parameters.contexts,
                        it
                )
                it.flush()
            }
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun rollbackCountSQL() {
        require(parameters.changesToRollback > 0) {
            "Liquibase: Changes to rollback must be greater than zero."
        }
        val outputFile = project.file(parameters.outputRollbackSQL)
        outputFile.parentFile.mkdirs()
        try {
            outputFile.bufferedWriter().use {
                instance.rollback(
                        parameters.changesToRollback,
                        parameters.contexts,
                        it
                )
                it.flush()
            }
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun generateChangeLog() {
        val outputChangeLogFile = project.file(parameters.outputChangeLogFile)
        outputChangeLogFile.parentFile.mkdirs()
        try {
            val diffOutputControl = DiffOutputControl(parameters.outputDefaultCatalog, parameters.outputDefaultSchema, true, null)
            CommandLineUtils.doGenerateChangeLog(
                    outputChangeLogFile.absolutePath,
                    database,
                    parameters.defaultCatalogName,
                    parameters.defaultSchemaName,
                    null,
                    parameters.outputChangeSetAuthor,
                    parameters.contexts,
                    if (parameters.dataDirectory != null) project.file(parameters.dataDirectory!!).absolutePath else null,
                    diffOutputControl
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun dbDoc() {
        val outputDbDocDir = project.file(parameters.outputDbDocDir)
        outputDbDocDir.mkdirs()
        try {
            instance.generateDocumentation(
                    outputDbDocDir.absolutePath,
                    parameters.contexts
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun tag() {
        try {
            instance.tag(
                    parameters.tag ?: throw IllegalArgumentException("Liquibase: Tag is required.")
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun tagExists() {
        val exists = try {
            instance.tagExists(
                    parameters.tag ?: throw IllegalArgumentException("Liquibase: Tag is required.")
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
        if (exists) {
            println("Tag ${parameters.tag} exists.")
        } else {
            println("Tag ${parameters.tag} does NOT exist.")
        }
    }

    private fun status() {
        try {
            instance.reportStatus(
                    true,
                    parameters.contexts,
                    System.out.writer()
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun validate() {
        try {
            instance.validate()
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun changelogSync() {
        try {
            instance.changeLogSync(
                    parameters.contexts
            )
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun changelogSyncSQL() {
        val outputFile = project.file(parameters.outputSyncSQL)
        outputFile.parentFile.mkdirs()
        try {
            outputFile.bufferedWriter().use {
                instance.changeLogSync(
                        parameters.contexts,
                        it
                )
                it.flush()
            }
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun listLocks() {
        val locks = try {
            instance.listLocks()
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
        locks.forEach {
            println("${it.id} ${it.lockGranted} ${it.lockedBy}")
        }
    }

    private fun releaseLocks() {
        try {
            instance.forceReleaseLocks()
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun dropAll() {
        try {
            instance.dropAll()
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }

    private fun clearCheckSums() {
        try {
            instance.clearCheckSums()
        } finally {
            try {
                database.rollback()
                database.close()
            } catch (e: Exception) {
                println("Liquibase: Problems in cleaning up.")
            }
        }
    }
}