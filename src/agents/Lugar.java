package agents;

import java.util.ArrayList;



import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import support.Descuento;


public class Lugar extends Agent {

	//parametros del lugar
	//el formato de los parametros es ciudad tipo categoria precio Decuento1 descuentoN --- Agencia1 AgenciaN ---
	//donde descuento es un string del tipo cant-min-max y agencia un string con el aid.

	private String ciudad, tipo;
	private Integer categoria;
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

			categoria = Integer.parseInt((String) args[2]);
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


		//agregando comportamiento para servir ofertas
		addBehaviour(new ServidorDeOfertas());

		//agregando comportamiento para realizar renegociaciones
		addBehaviour(new ServidorDeRenegociaciones());
		
		//agregando comportamiento para realizar confirmaciones
		addBehaviour(new ServidorDeConfirmaciones());
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

	private class ServidorDeOfertas extends CyclicBehaviour {
		public void action() {

			//filtramos solo mensajes CFP
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// Mensaje CFP recibido, se lo procesa. Se supone que viene tipo@categoria
				String requerimiento = msg.getContent();
				String[] requerimientoArray = requerimiento.split("@");
				ACLMessage reply = msg.createReply();

				boolean tipoCond = tipo.equals(requerimientoArray[0]) || requerimientoArray[0].equals("indefinido");
				boolean categoriaCond=  requerimientoArray[1].equals("indefinido") || categoria>=Integer.parseInt(requerimientoArray[1]);


				if (tipoCond && categoriaCond) {
					//El lugar cumple con el tipo (hotel, cabaña, etc) y la categoria deseada, proponer precio.
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(precio) +"@"+tipo+"@"+categoria);
				}
				else {
					// El lugar no cumple con la categoria o tipo de lugar.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("lugar-no-adecuado");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  

	private class ServidorDeRenegociaciones extends CyclicBehaviour {
		public void action() {

			//filtramos solo mensajes REJECT_PROPOSAL
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				//La propuesta fue rechazada. Se supone que viene el precioasuperar@cantidaddedias
				String requerimiento = msg.getContent();
				String[] requerimientoArray = requerimiento.split("@");
				Float precioASuperar = Float.parseFloat(requerimientoArray[0]);
				Integer cantidadDias = Integer.parseInt(requerimientoArray[1]);
				ACLMessage reply = msg.createReply();

				Float precioNuevo = Float.MAX_VALUE;
				float precioConDescuento;
				float porcentajeDescuento;

				//se recorren todos los descuentos y se ve cual es el primero que encaja con la cantidad
				//de personas y que supera el precio que le ofrecieron a la agencia.
				for (Descuento elem : descuentos){
					if (cantidadDias >= elem.getCantidad()){
						int i = elem.getMenorDescuento();

						while ( i<=elem.getMayorDescuento()){
							porcentajeDescuento = (float) (1 - (i/100.00));
							precioConDescuento = precio*porcentajeDescuento;

							if (precioASuperar>precioConDescuento){
								precioNuevo = precioConDescuento;
								break;
							}
							i++;
						}
						if (precioNuevo!=Float.MAX_VALUE){
							break;
						}
					}
				}

				if (precioNuevo!=Float.MAX_VALUE) {
					//Se encontro un descuento que encuadra y supera al precio a superar
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(precioNuevo)+"@"+tipo+"@"+categoria);
				}
				else {
					// No se puede superar el precio para esa cantidad de personas.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("no-se-puede-superar");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}
	
	private class ServidorDeConfirmaciones extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// la oferta fue aceptada
				String ofertaAceptada = msg.getContent();
				ACLMessage reply = msg.createReply();

				if (ofertaAceptada != null) {
					reply.setPerformative(ACLMessage.INFORM);
					//aqui iria un corrimiento de los descuentos (implementar si queda tiempo)
					System.out.println(ofertaAceptada);
					reply.setContent("Gracias");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}
}
