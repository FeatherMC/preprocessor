package com.replaymod.gradle.preprocess

import java.io.File

open class RootPreprocessExtension : ProjectGraphNodeDSL {
    var rootNode: ProjectGraphNode? = null
        get() = field ?: linkNodes()?.also { field = it }

    private val nodes = mutableSetOf<Node>()

    fun createNode(project: String, mcVersionName: String, mcVersion: Int, mappings: String): Node {
        return Node(project, mcVersionName, mcVersion, mappings).also { nodes.add(it) }
    }

    private fun linkNodes(): ProjectGraphNode? {
        val first = nodes.firstOrNull() ?: return null
        val visited = mutableSetOf<Node>()
        fun Node.breadthFirstSearch(): ProjectGraphNode {
            val graphNode = ProjectGraphNode(project, mcVersionName, mcVersion, mappings)
            links.forEach { (otherNode, extraMappings) ->
                if (visited.add(otherNode)) {
                    graphNode.links.add(Pair(otherNode.breadthFirstSearch(), extraMappings))
                }
            }
            return graphNode
        }
        return first.breadthFirstSearch()
    }

    override fun addNode(project: String, mcVersionName: String, mcVersion: Int, mappings: String, extraMappings: File?, invertMappings: Boolean): ProjectGraphNode {
        check(rootNode == null) { "Only one root node may be set." }
        check(extraMappings == null) { "Cannot add extra mappings to root node." }
        return ProjectGraphNode(project, mcVersionName, mcVersion, mappings).also { rootNode = it }
    }
}

class Node(
    val project: String,
    val mcVersionName: String,
    val mcVersion: Int,
    val mappings: String,
) {
    internal val links = mutableMapOf<Node, Pair<File?, Boolean>>()

    fun link(other: Node, extraMappings: File? = null) {
        this.links[other] = Pair(extraMappings, false)
        other.links[this] = Pair(extraMappings, true)
    }
}

interface ProjectGraphNodeDSL {
    operator fun String.invoke(mcVersion: Int, mcVersionName: String, mappings: String, extraMappings: File? = null, configure: ProjectGraphNodeDSL.() -> Unit = {}) {
        addNode(this, mcVersionName, mcVersion, mappings, extraMappings).configure()
    }

    fun addNode(project: String, mcVersionName: String, mcVersion: Int, mappings: String, extraMappings: File? = null, invertMappings: Boolean = false): ProjectGraphNodeDSL
}

open class ProjectGraphNode(
        val project: String,
        val mcVersionName: String,
        val mcVersion: Int,
        val mappings: String,
        val links: MutableList<Pair<ProjectGraphNode, Pair<File?, Boolean>>> = mutableListOf()
) : ProjectGraphNodeDSL {
    override fun addNode(project: String, mcVersionName: String, mcVersion: Int, mappings: String, extraMappings: File?, invertMappings: Boolean): ProjectGraphNodeDSL =
            ProjectGraphNode(project, mcVersionName, mcVersion, mappings).also { links.add(Pair(it, Pair(extraMappings, invertMappings))) }

    fun findNode(project: String): ProjectGraphNode? = if (project == this.project) {
        this
    } else {
        links.map { it.first.findNode(project) }.find { it != null }
    }

    fun findParent(node: ProjectGraphNode): Pair<ProjectGraphNode, Pair<File?, Boolean>>? = if (node == this) {
        null
    } else {
        links.map { (child, extraMappings) ->
            if (child == node) {
                Pair(this, extraMappings)
            } else {
                child.findParent(node)
            }
        }.find { it != null }
    }
}
