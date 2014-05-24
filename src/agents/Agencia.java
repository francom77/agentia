package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Agencia extends Agent {

	//poderacion calificacion y precio
	Float pesoCalif, pesoPrecio;

	protected void setup(){
		System.out.println("Agencia "+getAID().getName()+" esta lista.");
		Object[] args = getArguments();

		//se supone que viene le peso de la calificacion y el peso del precio
		if (args != null && args.length > 0) {
			pesoCalif = Float.parseFloat((String) args[0]);
			pesoPrecio = Float.parseFloat((String) args[1]);
		}

		// Registrando el servicio de agencia
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("agencia");
		sd.setName("JADE-agencia");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}


		//agregando comportamiento para servir requerimientos
		addBehaviour(new ServidorDeRequerimientosLugar());
	}

	protected void takeDown() {
		// desregistrando el servico
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
	}


	private class ServidorDeRequerimientosLugar extends Behaviour {

		//variable que controla el flujo de los comporotamientos
		private int step = 0;

		//------------LUGARES------------------
		//lugares con los que negocia
		private AID[] lugares;

		// Agente que ofrece el mejor lugar hasta ahora
		private AID mejorLugar; 

		// La mejor oferta de lugar hasta ahora
		private String[] mejorOfertaLugar; 

		// Cantidad de lugares que no quieren negociar mas
		private int cantRefuseLugar = 0;

		//requerimiento actual lugar
		private String[] requerimientoActualLugar;


		//template para filtrar los mensajes
		private MessageTemplate mt;

		//propuesta a turista
		private ACLMessage mensajeDeTurista = new ACLMessage();
		
		public void action() {

			switch (step){
			case 0:
				//obtener el mensaje cfp de los turistas y procesarlos
				mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CFP),
						MessageTemplate.MatchConversationId("lugar-trade"));
				ACLMessage msg = myAgent.receive(mt);
				
				if (msg != null) {
					
					//se usa despues para crear la respuesta
					mensajeDeTurista = msg;
					
					//supone que viene ciudad@tipo@categoria@cantdias@preciomaxporpersona
					String requerimiento = msg.getContent();
					requerimientoActualLugar = requerimiento.split("@");

					//inicia la negociacion. 
					// buscar los lugares
					DFAgentDescription templateLugar = new DFAgentDescription();
					ServiceDescription sdLugar = new ServiceDescription();
					String tipoLugar = "alquiler-"+requerimientoActualLugar[0]+"-"+getAID().getLocalName();
					sdLugar.setType(tipoLugar);
					templateLugar.addServices(sdLugar);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, templateLugar); 
						lugares = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							lugares[i] = result[i].getName();

						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					if (lugares.length == 0){
						//se setea el paso 5 (no se encontaron lugares) y se rompe el case
						step = 5;
						break;
					}

					//enviar los cfp a los lugares
					ACLMessage cfpLugar = new ACLMessage(ACLMessage.CFP);
					for (AID elem : lugares) {
						cfpLugar.addReceiver(elem);
					} 

					String contenido = requerimientoActualLugar[1]+"@"+requerimientoActualLugar[2];
					cfpLugar.setContent(contenido);
					cfpLugar.setConversationId("lugar-trade");
					cfpLugar.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
					myAgent.send(cfpLugar);

					//Preparar el template para recibir las propuestas
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("lugar-trade"), 
							MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
											MessageTemplate.MatchPerformative(ACLMessage.REFUSE)));

					step = 1;
				} else {
					block();
				}
				break;
			case 1:
				// Recibe Propose/Refuse de los lugares
				//Aqui ocurre la magia
				ACLMessage respuesta = myAgent.receive(mt);
				if (respuesta != null) {
					// respuesta recibida
					if (respuesta.getPerformative() == ACLMessage.PROPOSE) {
						// se trata de una oferta 
						String[] propuestaLugar =respuesta.getContent().split("@");
						Float valorOferta = this.calcularValorOferta(propuestaLugar);

						if (mejorLugar == null){
							// es la primer oferta, la guardamos como mejor
							mejorOfertaLugar = propuestaLugar;
							mejorLugar = respuesta.getSender();
							 
						} else if (valorOferta>(this.calcularValorOferta(mejorOfertaLugar))){
							//obtuvo una calificacion mayor que la actual
							this.actualizarMejorOferta(respuesta);
						} else if (valorOferta.equals(this.calcularValorOferta(mejorOfertaLugar))) {
							//obtuvieron igual calificacion, hay que ver cual tiene el mejor precio
							if (Float.parseFloat(mejorOfertaLugar[0]) > Float.parseFloat(propuestaLugar[0])){
								this.actualizarMejorOferta(respuesta);
							} else  {
								//no es una mejor oferta, se debe enviar el Reject
								this.enviarRefuse(respuesta);
							}
						} else {
							//no es una mejor oferta, se debe enviar el reject
							this.enviarRefuse(respuesta);
						}

					
						
					} else if (respuesta.getPerformative() == ACLMessage.REFUSE){
						cantRefuseLugar++;
					}
					if ((cantRefuseLugar == lugares.length - 1) | (cantRefuseLugar == lugares.length) ) {
						// todos contestaron REFUSE, menos uno, el que dio la mejor oferta
						// o bien todos contestaron con un refuse (nadie puede dar una oferta)
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Se envia el acept al lugar y agencia elegidos
				// Si no hubiera el comportamiento termina por la definicion de done()

				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(mejorLugar);
				order.setContent(mejorOfertaLugar[0] +"@"+mejorOfertaLugar[1]+"@"+mejorOfertaLugar[2]);
				order.setConversationId("lugar-trade");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("lugar-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// Receive the purchase order reply
				respuesta = myAgent.receive(mt);
				if (respuesta != null) {
					// se recibe el gracias
					if (respuesta.getPerformative() == ACLMessage.INFORM) {

						System.out.println("Lugar elegido, agente: "+respuesta.getSender().getName());
						//enviar la oferta del lugar al turista

					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}



		private float calcularValorOferta(String[] propuesta){

			Float precio = Float.parseFloat(propuesta[0]);
			Integer calif = Integer.parseInt(propuesta[2]);
			int precioCalif = this.calcularValorPrecio(precio);

			return pesoCalif*(calif) + pesoPrecio*(precioCalif);

		}

		private int calcularValorPrecio(Float precio){
			Float porcDif = (precio * 100) / (Float.parseFloat(requerimientoActualLugar[4]));

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
		private float calcularPrecioASuperar(String[] ofertaActual){

			if (ofertaActual[2].equals(this.mejorOfertaLugar[2])){
				//tienen la misma categoria debe superar el precio actual
				return Float.parseFloat(this.mejorOfertaLugar[0]);
			}
			
			float  valorMejorOferta  = calcularValorOferta(this.mejorOfertaLugar);
			int califPrecioASuperar = (int) Math.ceil((valorMejorOferta - pesoCalif*Integer.parseInt(ofertaActual[2]))/0.8);


			if (califPrecioASuperar <= 5){
				//no tienen la misma, se devuelve un valor segun la categoria caclulada
				switch (califPrecioASuperar){
				case 1: return Float.parseFloat(requerimientoActualLugar[4]);
				case 2: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.95);
				case 3: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.90);
				case 4: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.85);
				default: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.84);

				} 
			} else {
				//se devuelve un valor negativo de precio a superar para forzar el refuse ya que nunca va a poder obtener una calificación mayor a 5.
				return -100;
			}

		}
		
		private void actualizarMejorOferta (ACLMessage respuesta){
			
			//se le debe enviar reject al que estaba antes como mejor y cambiar al nuevo
			ACLMessage rejectLugar = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectLugar.setConversationId("lugar-trade");
			rejectLugar.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
			//agrego al receptor al que antes era mejor lugar
			rejectLugar.addReceiver(mejorLugar);
			//actualizo el mejor lugar
			mejorLugar = respuesta.getSender();
			
			//alamaceno al oferta del que era el mejor lugar
			String [] mejorAnterior = mejorOfertaLugar;
			//Actualizo la mejor oferta
			mejorOfertaLugar = respuesta.getContent().split("@");
			//Creo el contenido del reject con la oferta anterior
			rejectLugar.setContent(calcularPrecioASuperar(mejorAnterior)+"@"+requerimientoActualLugar[3]);
			
			myAgent.send(rejectLugar);
		}
		
		private void enviarRefuse(ACLMessage respuesta){
			ACLMessage rejectLugar = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectLugar.setConversationId("lugar-trade");
			rejectLugar.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
			rejectLugar.addReceiver(respuesta.getSender());
			rejectLugar.setContent(calcularPrecioASuperar(respuesta.getContent().split("@"))+"@"+requerimientoActualLugar[3]);
			
			myAgent.send(rejectLugar);
		}

		public boolean done() {
			ACLMessage respuesta = mensajeDeTurista.createReply();
			if (step == 2 && mejorLugar == null) {
				respuesta.setPerformative(ACLMessage.REFUSE);
				respuesta.setContent("no-hay-ofertas-ciudad-tipo");
				
			}
			else if (step==5){
				respuesta.setPerformative(ACLMessage.REFUSE);
				respuesta.setContent("agencia-sin-contactos");
			}
			else if (step==4){
				respuesta.setPerformative(ACLMessage.PROPOSE);
				
				//se envia precio@tipo@categoria@idlocaldellugar
				String contenido = mejorOfertaLugar[0]+"@"+mejorOfertaLugar[1]+"@"+mejorOfertaLugar[2]+"@"+mejorLugar.getLocalName();
				respuesta.setContent(contenido);
			}
			

			if ((step == 2 && mejorLugar == null) || step == 4 || step == 5){
				
				//enviando mensaje al turista
				myAgent.send(respuesta);
				//reiniciando variables para la proxima ejecucion del comportamiento

				step = 0;
				lugares = null;
				mejorLugar = null;
				mejorOfertaLugar = null;
				cantRefuseLugar = 0;
				requerimientoActualLugar = null;
				mt = null;
			}

			return ((step == 2 && mejorLugar == null) || step == 4 || step == 5);
		}
	}
}



