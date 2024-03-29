package snuggle.toomanylimits.representation.passes.lexing

data class Loc(val startRow: Int, val startCol: Int, val endRow: Int, val endCol: Int, val fileName: String) {

    fun merge(other: Loc): Loc {
        if (fileName != other.fileName)
            throw IllegalStateException("Locs have different file names but are merged? Bug in compiler, please report!")

        val start = getFirst(startRow, startCol, other.startRow, other.startCol)
        val end = getLast(endRow, endCol, other.endRow, other.endCol)
        return Loc(start.first, start.second, end.first, end.second, fileName)
    }

    override fun toString(): String = "$startRow:$startCol in file \"$fileName\""

    companion object {

        // A loc which should never end up as part of an error report
        val NEVER: Loc = Loc(-1,-3,-3,-7,"Should never happen. If you see this location in an error, there's a bug in the compiler - please report!")

        private fun getFirst(row1: Int, col1: Int, row2: Int, col2: Int): Pair<Int, Int> {
            return if (row1 < row2)
                Pair(row1, col1)
            else if (row2 < row1)
                Pair(row2, col2)
            else if (col1 < col2)
                Pair(row1, col1)
            else
                Pair(row2, col2)
        }

        private fun getLast(row1: Int, col1: Int, row2: Int, col2: Int): Pair<Int, Int> {
            return if (row1 < row2)
                Pair(row2, col2)
            else if (row2 < row1)
                Pair(row1, col1)
            else if (col1 < col2)
                Pair(row2, col2)
            else
                Pair(row1, col1)
        }
    }
}