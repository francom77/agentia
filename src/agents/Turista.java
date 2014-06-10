package agents;

import gui.Principal;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Turista extends Agent {

	// RequermientoTurista
	private String[] requerimientoTuristaLugar;
	private String[] requerimientoTuristaTransporte = new String[5];

	// Lista de agencias
	private AID[] agencias;

	//peso que le va a dar a la calificacion y al precio
	float pesoCalifLugar, pesoPrecioLugar, pesoCalifTransporte, pesoPrecioTransporte;

	protected void setup(){
		System.out.println("Turista "+getAID().getName()+" esta listo.");

		Object[] args = getArguments();

		////ciudad tipo categoria cantdias preciomaxporpersona tipo categoria cantpersonas preciomaxporpersona
		if (args != null && args.length == 9) {

			requerimientoTuristaLugar = new String[5];

			requerimientoTuristaLugar[0] = (String) args[0];
			requerimientoTuristaLugar[1] = (String) args[1];
			requerimientoTuristaLugar[2] = (String) args[2];
			requerimientoTuristaLugar[3] = (String) args[3];
			requerimientoTuristaLugar[4] = (String) args[4];

			requerimientoTuristaTransporte = new String[5];

			requerimientoTuristaTransporte[0] = (String) args[0];
			requerimientoTuristaTransporte[1] = (String) args[5];
			requerimientoTuristaTransporte[2] = (String) args[6];
			requerimientoTuristaTransporte[3] = (String) args[7];
			requerimientoTuristaTransporte[4] = (String) args[8];

			Float [] pesosLugar = this.calcularPesos(Integer.parseInt(requerimientoTuristaLugar[2]));
			Float [] pesosTransporte = this.calcularPesos(Integer.parseInt(requerimientoTuristaTransporte[2]));

			pesoCalifLugar = pesosLugar[0];
			pesoPrecioLugar = pesosLugar[1];
			pesoCalifTransporte = pesosTransporte[0];
			pesoPrecioTransporte = pesosTransporte[1];


			//buscar las agencias disponibles
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("agencia");
			template.addServices(sd);
			try {
				DFAgentDescription[] result = DFService.search(this, template); 
				agencias = new AID[result.length];
				for (int i = 0; i < result.length; ++i) {
					agencias[i] = result[i].getName();

				}
			}
			catch (FIPAException fe) {
				fe.printStackTrace();
			}
			
			addBehaviour(new ServidorDeNegociaciones());
		}else {
			// Parametros mal
			System.out.println("Verifique los parametros");
			doDelete();
		}
	}

	private Float[] calcularPesos(Integer categoria){
		Float[] pesos = new Float[2];
		if (categoria > 3){
			//turista vip
			pesos [0] = (float) 0.8;
			pesos [1] = (float) 0.2;

		}else if (categoria == 3){
			//turista medio
			pesos [0] = (float) 0.5;
			pesos [1] = (float) 0.5;
		}else if (categoria < 3){
			//turista economico
			pesos [0] = (float) 0.2;
			pesos [1] = (float) 0.8;
		}

		return pesos;
	}

	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Agente turista "+getAID().getName()+" finalizado.");
	}

	private class ServidorDeNegociaciones extends Behaviour {	

		//la agencia que da la mejor oferta hasta ahora
		private AID mejorAgencia;
		//la mejor oferta hasta ahora
		private String mejorOferta;
		//cantidad de respuestas recibidas
		private int cantRespuestas = 0;
		//template para filtrar mensajes
		private MessageTemplate mt; 
		////variable que controla el flujo del comportamiento
		private int step = 0;

		public void action() {

			switch (step) {
			case 0:
				//enviar cfp a las agencias
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < agencias.length; ++i) {
					cfp.addReceiver(agencias[i]);
				}

				//renombro por facilidad
				String[] r1 = requerimientoTuristaLugar;
				String[] r2 = requerimientoTuristaTransporte;
				String contenido = r1[0]+"@"+r1[1]+"@"+r1[2]+"@"+r1[3]+"@"+r1[4]+"---";
				contenido = contenido + r2[0]+"@"+r2[1]+"@"+r2[2]+"@"+r2[3]+"@"+r2[4];

				cfp.setContent(contenido);
				cfp.setConversationId("agencia-trade");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("agencia-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			
			case 1:
				// Se reciben propose o refuse de las agencias
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Respuesta recibida
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// Se trata de una oferta
						float valorOferta = this.calcularValorOferta(reply.getContent());
						if (mejorAgencia == null || valorOferta > this.calcularValorOferta(mejorOferta)) {
							// Esta es la mejor oferta hasta ahora
							mejorOferta= reply.getContent();
							mejorAgencia = reply.getSender();
						}
					}
					cantRespuestas++;
					if (cantRespuestas >= agencias.length) {
						// se recibieron todas las respuestas de las agencias
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			
			case 2:
				// Enviar el acept a la agencia que gano
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(mejorAgencia);
				order.setContent(mejorOferta);
				order.setConversationId("agencia-trade");
				order.setReplyWith("acept"+System.currentTimeMillis());
				myAgent.send(order);
				System.out.println(mejorOferta +"  Agencia:" + mejorAgencia.getLocalName());
                                step = 3;
				break;
			}
		}
		
		private int calcularValorPrecioLugar(Float precio){
			Float porcDif = (precio * 100) / (Float.parseFloat(requerimientoTuristaLugar[4]));

			if (porcDif>100){
				return -100;
			}else if (porcDif==100){
				return 1;
			} else if (porcDif>=95){
				return 2;
			}else if (porcDif>=90){
				return 3;
			}else if (porcDif>=85){
				return 4;
			}else{
				return 5;
			}
		}
		
		private int calcularValorPrecioTransporte(Float precio){
			Float porcDif = (precio * 100) / (Float.parseFloat(requerimientoTuristaTransporte[4]));

			if (porcDif>100){
				return -100;
			}else if (porcDif==100){
				return 1;
			} else if (porcDif>=95){
				return 2;
			}else if (porcDif>=90){
				return 3;
			}else if (porcDif>=85){
				return 4;
			}else{
				return 5;
			}
		}
		
		private float calcularValorOferta(String propuesta){
			
			String[] propuestaLugar = propuesta.split("---")[0].split("@");
			String[] propuestaTransporte = propuesta.split("---")[1].split("@");
			
			Integer califLugar = Integer.parseInt(propuestaLugar[2]);
			Float precioLugar = Float.parseFloat(propuestaLugar[0]);
			int precioCalifLugar = this.calcularValorPrecioLugar(precioLugar);
			
			Float valorLugar = pesoCalifLugar*(califLugar) + pesoPrecioLugar*(precioCalifLugar);
			
			Integer califTransporte = Integer.parseInt(propuestaTransporte[2]);
			Float precioTransporte = Float.parseFloat(propuestaTransporte[0]);
			int precioCalifTransporte = this.calcularValorPrecioTransporte(precioTransporte);
			
			Float valorTransporte = pesoCalifTransporte*(califTransporte) + pesoPrecioTransporte*(precioCalifTransporte);
			
			return valorLugar + valorTransporte;

		}
		
		public boolean done() {
			if (step == 2 && mejorAgencia == null) {
				System.out.println("No hay ofertas disponibles");
                                Principal.setRespuesta("No hay ofertas disponibles");
                                myAgent.doDelete();
			}
                        if (step == 3){
                                Principal.setRespuesta(mejorOferta + "---" + mejorAgencia.getLocalName());
                                myAgent.doDelete();
                        }
			return ((step == 2 && mejorAgencia == null) || step == 3);
		}
	}
}