module ogive(L,R) { 
 nof=L/2;
	phi = (pow(R,2) + pow(L,2))/ (2*R);
 	$fn=100;
H2=L/nof;
x1= 0;
x2=0;
A1=0;
A2= 0;
	for (i=[1:nof]) {
		assign(x1 = ((L/nof)*(i-1)),
		x2 = ((L/nof)*i),
		A1= sqrt(pow(phi,2) - pow((L - ((L/nof)*(i-1))), 2) ) + (R - phi),
		A2= sqrt(pow(phi,2) - pow((L - ((L/nof)*i)), 2) ) + (R - phi),
		H1= (L/nof)*i)
		{
			translate ([0,0, L- H1]) cylinder (r1 = A2, r2 = A1 , h= H2);
		}
	}
}
difference() {
ogive(336.55,39.37);
translate([0,0,-3.175])ogive(336.55,36.19);
}

