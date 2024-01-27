package builtins.helpers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

data class Fraction(val numerator: BigInteger, val denominator: BigInteger): Number(), Comparable<Fraction> {

    companion object {
        fun parse(string: String): Fraction {
            val dotIndex = string.indexOf('.')
            if (dotIndex == -1) return Fraction(BigInteger(string), BigInteger.ONE)
            val numerator = BigInteger(string.substring(0, dotIndex) + string.substring(dotIndex + 1))
            val denomPower = string.length - dotIndex - 1
            val denominator = BigInteger.TEN.pow(denomPower)
            return reduce(numerator, denominator)
        }
    }


    override fun toByte(): Byte = (numerator / denominator).toByte()
    override fun toChar(): Char = this.toInt().toChar()
    override fun toShort(): Short = (numerator / denominator).toShort()
    override fun toInt(): Int = (numerator / denominator).toInt()
    override fun toLong(): Long = (numerator / denominator).toLong()
    override fun toFloat(): Float = BigDecimal(numerator).divide(BigDecimal(denominator), MathContext(10)).toFloat()
    override fun toDouble(): Double = BigDecimal(numerator).divide(BigDecimal(denominator), MathContext(20)).toDouble()

    override fun compareTo(other: Fraction): Int = when {
        denominator == other.denominator -> numerator.compareTo(other.numerator)
        else -> (numerator * other.denominator).compareTo(other.numerator * denominator)
    }

    operator fun plus(other: Fraction) = reduce(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun minus(other: Fraction) = reduce(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)
    operator fun times(other: Fraction) = reduce(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Fraction) = reduce(numerator * other.denominator, denominator * other.numerator)
    operator fun rem(other: Fraction) = reduce((numerator * other.denominator) % (other.numerator * denominator), denominator * other.denominator)

    operator fun unaryMinus() = Fraction(-numerator, denominator)

}

private fun reduce(numerator: BigInteger, denominator: BigInteger): Fraction {
    val gcd = numerator.gcd(denominator)
    return if (gcd == BigInteger.ONE)
        Fraction(numerator, denominator)
    else
        Fraction(numerator / gcd, denominator / gcd)
}