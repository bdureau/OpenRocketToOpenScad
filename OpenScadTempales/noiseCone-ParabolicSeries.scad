/*
*
* Description: this is a simple Power serie noise cone generated for OpenScad
*               you can compile it and export it to an STL file so that it can be 3d printed
*               Note that compiling with openScad can take a long time
*
*
 y = R* ((2*(x/L)-K'*(x/L)^2)/(2-K'))

*/
length = ##length##;
aftradius = ##aftradius##;
thickness = ##thickness##;
Rshoulder=##aftshoulderradius##;
Hshoulder=##aftshoulderlength##;
TShoulder=##aftshoulderthickness##;
K=##N##;
/*length = 100;
aftradius = 10;
thickness = 1.5;
Rshoulder=8.5;
Hshoulder=10;
TShoulder=1.5;
K=0.5;*/
$fn=200;

module ogive(L,R,k) { 
    nof=L/2;
    H2=L/nof;
    A1=0;
    A2= 0;
	for (i=[1:nof]) {
		assign(
		A1 = R *((2*(((L/nof)*(i-1))/L)-K*pow((((L/nof)*(i-1))/L),2))/(2-K)),
        A2 = R *((2*(((L/nof)*i)/L)-K*pow((((L/nof)*i)/L),2))/(2-K)),
		H1= (L/nof)*i)
		{
			translate ([0,0, L- H1]) cylinder (r1 = A2, r2 = A1 , h= H2);
		}
	}
}

//noise cone itself
difference() {
ogive(length,aftradius,K );
translate([0,0,-thickness])ogive(length,aftradius,K-thickness);
}

//shoulder
translate([0,0,-Hshoulder/2])difference() {
    cylinder(r=Rshoulder, h=Hshoulder, center = true);
    cylinder(r=Rshoulder-thickness, h=Hshoulder, center =true);
}