/*
*
* Description: this is a simple Power serie noise cone generated for OpenScad
*               you can compile it and export it to an STL file so that it can be 3d printed
*               Note that compiling with openScad can take a long time
*
*
 y = R(x/L)^n
Where:

    n = 1 for a cone
    n = 0.75 for a 3/4 power
    n = 0.5 for a 1/2 power (parabola)
    n = 0 for a cylinder 
*/

length = ##length##;
aftradius = ##aftradius##;
thickness = ##thickness##;
Rshoulder=##aftshoulderradius##;
Hshoulder=##aftshoulderlength##;
TShoulder=##aftshoulderthickness##;
N=##N##;
/*length = 100;
aftradius = 10;
thickness = 1.5;
Rshoulder=8.5;
Hshoulder=10;*/
$fn=200;
module ogive(L,R,n) { 
    nof=L/2;
    H2=L/nof;
	for (i=[1:nof]) {
		assign(
        A1 = R* pow(((L/nof)*(i-1)/L),n),
		A2 = R* pow(((L/nof)*(i)/L),n),
		H1= (L/nof)*i)
		{
			translate ([0,0, L- H1]) cylinder (r1 = A2, r2 = A1 , h= H2, center = true);
		}
	}
}

//noise cone itself
difference() {
ogive(length,aftradius,N);
translate([0,0,-thickness])ogive(length,aftradius-thickness, N);
}

//shoulder
translate([0,0,-Hshoulder/2])difference() {
    cylinder(r=Rshoulder, h=Hshoulder, center = true);
    cylinder(r=Rshoulder-thickness, h=Hshoulder, center =true);
}

