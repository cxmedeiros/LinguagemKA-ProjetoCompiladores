java -cp out ka.Ka
<<<
var x = 10;
fun dobra(n) { return n * 2; }
print dobra(x);
print y;
print dobra(x + 1);
var c = nil;
fun mk() { var n = 0; return fun() { n = n + 1; return n; }; }
c = mk();
print c();
print c();
fun mk2() { var k = 100; fun interna() { return k; } return interna; }
var h = mk2();
print h();
{ var local = 1; print local; }
print local;
var x = "redefinida";
print x;
fun usaX() { return x; }
x = "mudou";
print usaX();
fun recursiva(n) { if (n == 0) return 0; return n + recursiva(n - 1); }
print recursiva(10);
print 1 / 0;
print "depois do erro";
var o = { v: 1, m: fun() { return this.v; } };
print o.m();
o.v = 5;
print o.m();
print match (o) { case { v: Int(q) } => q; };
fun comAninhada() { var a = 1; return fun() { return fun() { a = a + 1; return a; }; }; }
var e = comAninhada()();
print e(); print e();
print nao_existe;
print c();
>>> /ka> ka> ka> 20[[:space:]]*ka> ka> 22[[:space:]]*ka> ka> ka> ka> 1[[:space:]]*ka> 2[[:space:]]*ka> ka> ka> 100[[:space:]]*ka> 1[[:space:]]*ka> ka> ka> redefinida[[:space:]]*ka> ka> ka> mudou[[:space:]]*ka> ka> 55[[:space:]]*ka> ka> depois do erro[[:space:]]*ka> ka> 1[[:space:]]*ka> ka> 5[[:space:]]*ka> 5[[:space:]]*ka> ka> ka> 2[[:space:]]*3[[:space:]]*ka> ka> 3[[:space:]]*ka> /
>>>2 /.*Variavel indefinida 'y'.*/
>>>= 0