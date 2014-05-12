package agents;

import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import support.Descuento;


public class Lugar extends Agent {

	//parametros del lugar
	//el formato de los parametros es ciudad tipo categoria precio Decuento1 descuentoN --- Agencia1 AgenciaN ---
	//donde descuento es un string del tipo cant-min-max y agencia un string con el aid.
	
	private String ciudad, tipo, categoria;
	private float precio;
	private ArrayList<Descuento> descuentos;
	private ArrayList<String> agencias;

	protected void setup(){
		System.out.println("Agente Lugar "+getAID().getName()+" fue creado.");

		//obteniendo los parametros por la consola
		Object[] args = getArguments();
		if (args != null && args.length > 0) {

			ciudad = (String) args[0];
			System.out.println("La ciudad es "+ciudad);

			tipo = (String) args[1];
			System.out.println("El tipo es "+ tipo);

			categoria = (String) args[2];
			System.out.println("La categoria es "+categoria);

			precio = Float.parseFloat((String)args[3]);
			System.out.println("El precio es "+precio);
			
			descuentos = new ArrayList<Descuento>();

			//se asume que los parametros desde la posicion 4 hasta una marca --- son los descuentos
			
			int i=4;
			while (!args[i].equals("---")){
				
				//obteninedo los valores de cant, menor descuento y mayor descuento
				String descuentoString = (String) args[i];
				String[] descuentoArray = descuentoString.split("-");
				int cant = Integer.parseInt(descuentoArray[0]);
				int menorDescuento = Integer.parseInt(descuentoArray[1]);
				int mayorDescuento = Integer.parseInt(descuentoArray[2]);

				Descuento descuento = new Descuento(cant, menorDescuento, mayorDescuento);

				descuentos.add(descuento);
				
				i++;
			}
			
			//luego de la marca de fin de descuento vienen los AID de las agencias con las que trabaja
			
			agencias = new ArrayList<String>();
			
			i++;
			while (!args[i].equals("---")){
				
				//obteninedo el AID
				String idAgencia = (String) args[i];
				agencias.add(idAgencia);
				i++;
			}
			
		}

		// Registrando el vendedor en las paginas amarillas
				DFAgentDescription dfd = new DFAgentDescription();
				dfd.setName(getAID());
				
				//se debe agregar un sd por cada agencia con la que va a trabajar. En principio se va a probar con todas
				
				for (String elem: this.agencias){
					ServiceDescription sd = new ServiceDescription();
					String tipo = "alquiler-"+this.ciudad+"-"+elem;
					sd.setType(tipo);
					sd.setName("JADE-alquiler-"+elem);
					dfd.addServices(sd);
				}
				
				try {
					DFService.register(this, dfd);
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}

					
		//aqui se agregan los comporatimientos
	}

	
	protected void takeDown() {
		// Desregistrando de las paginas amarillas.
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		System.out.println("Agente Lugar "+getAID().getName()+" finalizado.");
	}

}
