package dev.iurysouza.modulegraph

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.logging.Logger

/**
 * Generates a Mermaid graph for module dependencies.
 * - Groups: Folders containing modules
 * - Subgraphs: Grouped modules in the graph
 * - Source: Module with dependencies
 * - Target: Dependent module
 * - Arrows: Represent dependencies between modules
 */

fun buildMermaidGraph(
    theme: Theme,
    dependencies: MutableMap<String, List<String>>,
): String {
    val projectPaths = dependencies
        .entries
        .asSequence()
        .flatMap { (source, target) -> target.plus(source) }
        .sorted()

    val mostMeaningfulGroups: List<String> = projectPaths
        .map { it.split(":").takeLast(2).take(1) }
        .distinct()
        .flatten()
        .toList()

    val projectNames = projectPaths
        .map { listOf(it.split(":").takeLast(2)) }
        .distinct()
        .flatten()
        .toList()

    val subgraphs = mostMeaningfulGroups.joinToString("\n") { group ->
        createSubgraph(group, projectNames)
    }

    var arrows = ""

    dependencies.forEach { (source, targets) ->
        targets.forEach { target ->
            val sourceName = source.split(":").last { it.isNotBlank() }
            val targetName = target.split(":").last { it.isNotBlank() }
            if (sourceName != targetName) {
                arrows += "  $sourceName --> $targetName\n"
            }
        }
    }

    val mermaidConfig = """
      %%{
        init: {
          'theme': '${theme.value}'
        }
      }%%
    """.trimIndent()
    return "${mermaidConfig}\n\ngraph LR\n$subgraphs\n$arrows"
}

// Generate a Mermaid subgraph for the specified group and list of modules
fun createSubgraph(group: String, modules: List<List<String>>): String {
    // Extract module names for the current group
    val moduleNames = modules.asSequence()
        .map { it.takeLast(2) } // group by the most relevant part of the path
        .filter { it.all { it.length > 2 } }
        .filter { it.contains(group) }
        .map { it.last() } // take the actual module name
        .joinToString("\n    ") { moduleName -> moduleName }

    if (moduleNames.isBlank()) {
        return ""
    }
    return "  subgraph $group\n    $moduleNames\n  end"
}

fun appendMermaidGraphToReadme(
    mermaidGraph: String,
    readMeSection: String,
    readmeFile: File,
    logger: Logger,
) {
    val readmeLines: MutableList<String> = readmeFile.readLines().toMutableList()

    val modulesIndex = readmeLines.indexOfFirst { it.startsWith(readMeSection) }

    if (modulesIndex != -1) {
        val endIndex = readmeLines.drop(modulesIndex + 1).indexOfFirst { it.startsWith("#") }
        val actualEndIndex =
            if (endIndex != -1) endIndex + modulesIndex + 1 else readmeLines.size

        readmeLines.subList(modulesIndex + 1, actualEndIndex).clear()
        readmeLines.add(modulesIndex + 1, "```mermaid\n$mermaidGraph\n```")

        readmeFile.writeText(readmeLines.joinToString("\n"))
        logger.debug("Module graph added to ${readmeFile.path} under the $readMeSection section")
    } else {
        logger.debug("The $readMeSection section was not found in ${readmeFile.path}")
    }
}

fun Project.parseProjectStructure(): HashMap<String, List<String>> {
    val dependencies = hashMapOf<String, List<String>>()
    project.allprojects.forEach { sourceProject ->
        sourceProject.configurations.forEach { config ->
            config.dependencies.withType(ProjectDependency::class.java)
                .map { it.dependencyProject }
                .forEach { targetProject ->
                    dependencies[sourceProject.path] =
                        dependencies.getOrDefault(sourceProject.path, emptyList())
                            .plus(targetProject.path)
                }
        }
    }
    return dependencies
}
