import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep2SanteComponent } from './claim-step2-sante.component';

describe('ClaimStep2SanteComponent', () => {
  let component: ClaimStep2SanteComponent;
  let fixture: ComponentFixture<ClaimStep2SanteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep2SanteComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep2SanteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
