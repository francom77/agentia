package support;

public class Descuento {
	
	private int cantidad, menorDescuento, mayorDescuento;

	public Descuento(int cantidad, int menorDescuento, int mayorDescuento) {
		this.cantidad = cantidad;
		this.menorDescuento = menorDescuento;
		this.mayorDescuento = mayorDescuento;
	}
	
	public int getCantidad() {
		return cantidad;
	}

	public void setCantidad(int cantidad) {
		this.cantidad = cantidad;
	}

	public int getMenorDescuento() {
		return menorDescuento;
	}

	public void setMenorDescuento(int menorDescuento) {
		this.menorDescuento = menorDescuento;
	}

	public int getMayorDescuento() {
		return mayorDescuento;
	}

	public void setMayorDescuento(int mayorDescuento) {
		this.mayorDescuento = mayorDescuento;
	}
	
}
