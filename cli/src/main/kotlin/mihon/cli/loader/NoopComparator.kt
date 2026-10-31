package mihon.cli.loader

import java.util.Comparator

object NoopComparator : Comparator<Any?> {
    override fun compare(o1: Any?, o2: Any?): Int = 0
}
