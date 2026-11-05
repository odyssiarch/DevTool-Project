package com.devtool.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Node pohon paket. Daun (tanpa anak) = class. */
class TreeNode(
    val label: String,          // "com.google.android" (rantai paket tunggal digabung)
    val path: String,           // "com/google/android", unik, dipakai sebagai key
    val count: Int,             // total class di bawah node ini
    val children: List<TreeNode>
) {
    val isClass get() = children.isEmpty()
}

private class Mut { val kids = java.util.TreeMap<String, Mut>() }

/** Bangun pohon dari daftar nama class ("com/foo/Bar" atau "com.foo.Bar").
 *  Panggil di background (ViewModel / Dispatchers.Default), jangan di composable. */
fun buildTree(classNames: List<String>): List<TreeNode> {
    val root = Mut()
    for (n in classNames) {
        var cur = root
        for (p in n.removeSuffix(".class").split('/', '.')) {
            if (p.isNotEmpty()) cur = cur.kids.getOrPut(p) { Mut() }
        }
    }
    return root.kids.map { (name, m) -> freeze(name, "", m) }
        .sortedWith(compareBy({ it.isClass }, { it.label.lowercase() }))
}

private fun freeze(name: String, parent: String, m: Mut): TreeNode {
    var label = name
    var cur = m
    // gabung rantai paket yang cuma punya 1 anak paket: com -> google -> android jadi "com.google.android"
    while (cur.kids.size == 1 && cur.kids.values.first().kids.isNotEmpty()) {
        val (n, next) = cur.kids.entries.first()
        label += ".$n"
        cur = next
    }
    val path = (if (parent.isEmpty()) "" else "$parent/") + label.replace('.', '/')
    val kids = cur.kids.map { (n, k) -> freeze(n, path, k) }
        .sortedWith(compareBy({ it.isClass }, { it.label.lowercase() }))
    val count = if (kids.isEmpty()) 1 else kids.sumOf { it.count }
    return TreeNode(label, path, count, kids)
}

/** Filter by nama class (bukan nama paket). Paket tanpa hasil dibuang. */
fun filterTree(nodes: List<TreeNode>, q: String): List<TreeNode> = nodes.mapNotNull { n ->
    if (n.isClass) {
        n.takeIf { it.label.contains(q, ignoreCase = true) }
    } else {
        val kids = filterTree(n.children, q)
        if (kids.isEmpty()) null else TreeNode(n.label, n.path, kids.sumOf { it.count }, kids)
    }
}

private class Row(val node: TreeNode, val depth: Int)

private fun flatten(
    nodes: List<TreeNode>, open: Set<String>, all: Boolean, depth: Int, out: MutableList<Row>
) {
    for (n in nodes) {
        out += Row(n, depth)
        if (!n.isClass && (all || n.path in open)) flatten(n.children, open, all, depth + 1, out)
    }
}

/**
 * Pemakaian di BrowseScreen:
 *   val roots = vm.tree.collectAsState().value      // hasil buildTree() di background
 *   val shown = if (query.length >= 2) filterTree(roots, query) else roots
 *   PackageTree(shown, expandAll = query.length >= 2) { cls -> openClass(cls) }
 */
@Composable
fun PackageTree(
    roots: List<TreeNode>,
    expandAll: Boolean = false,
    modifier: Modifier = Modifier,
    onClassClick: (String) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val rows = remember(roots, open, expandAll) {
        buildList { flatten(roots, open, expandAll, 0, this) }
    }
    LazyColumn(modifier) {
        items(rows, key = { it.node.path }) { r ->
            val n = r.node
            val isOpen = expandAll || n.path in open
            TreeRow(
                node = n,
                depth = r.depth,
                expanded = isOpen,
                onClick = {
                    if (n.isClass) onClassClick(n.path.replace('/', '.'))
                    else open = if (n.path in open) open - n.path else open + n.path
                }
            )
        }
    }
}

@Composable
private fun TreeRow(node: TreeNode, depth: Int, expanded: Boolean, onClick: () -> Unit) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    val rot by animateFloatAsState(if (expanded) 90f else 0f, label = "chev")
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .drawBehind {
                // garis vertikal tiap level, sejajar tengah ikon induk
                for (i in 0 until depth) {
                    val x = (i * 16 + 18).dp.toPx()
                    drawLine(guide, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
            }
            .padding(start = (depth * 16 + 8).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isClass) {
            Spacer(Modifier.width(20.dp))
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(20.dp).rotate(rot),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            when {
                node.isClass -> Icons.Outlined.Code
                expanded -> Icons.Outlined.FolderOpen
                else -> Icons.Outlined.Folder
            },
            null,
            Modifier.size(20.dp),
            tint = if (node.isClass) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            node.label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!node.isClass) {
            Text(
                "${node.count}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
